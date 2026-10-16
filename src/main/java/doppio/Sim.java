package doppio;

import doppio.backend.SimulatorBackend;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class Sim {
    private final SimulatorBackend backend;
    private Phase phase = Phase.OBSERVE;
    private final List<Thread> forkedThreads = new ArrayList<>();
    private final Set<Thread> agents = new HashSet<>();
    private boolean running;
    private int activeAgents;
    private int driveArrivals;
    private int stepArrivals;
    private int stepGeneration;
    private Throwable failure;

    public Sim(SimulatorBackend backend) {
        this.backend = backend;
    }

    public InPort inPort(String path) {
        return new InPort(this, path);
    }

    public OutPort outPort(String path) {
        return new OutPort(this, path);
    }

    public synchronized long time() {
        return backend.time();
    }

    public SimulatorBackend backend() {
        return backend;
    }

    public void run(Consumer<Sim> body) {
        Objects.requireNonNull(body, "body");
        startRun();
        try {
            try {
                body.accept(this);
            } catch (Throwable t) {
                recordFailure(t);
            } finally {
                deregisterAgent();
            }
            joinForkedThreads();
            rethrowFailure();
        } finally {
            finishRun();
        }
    }

    public void fork(Runnable body) {
        Objects.requireNonNull(body, "body");
        Thread thread;
        synchronized (this) {
            if (!running) {
                throw new IllegalStateException("fork is only allowed inside Sim.run(...)");
            }
            requireAgent();
            rethrowFailure();
            if (phase != Phase.OBSERVE) {
                throw new IllegalStateException("fork is only allowed during the observe phase");
            }
            thread = new Thread(() -> runForkedAgent(body), "doppio-agent-" + forkedThreads.size());
            activeAgents++;
            agents.add(thread);
            forkedThreads.add(thread);
            try {
                thread.start();
            } catch (RuntimeException | Error e) {
                agents.remove(thread);
                forkedThreads.remove(thread);
                activeAgents--;
                recordFailure(e);
                throw e;
            }
        }
    }

    public synchronized void drive() {
        requireAgent();
        if (running) {
            rethrowFailure();
        }
        if (!running) {
            phase = Phase.DRIVE;
            return;
        }
        if (phase == Phase.DRIVE) {
            return;
        }

        driveArrivals++;
        releaseDrivePhaseIfReady();
        while (phase != Phase.DRIVE && failure == null) {
            awaitPhaseChange();
        }
        rethrowFailure();
    }

    public void drive(Runnable drives) {
        drive();
        drives.run();
    }

    public synchronized void step() {
        requireAgent();
        if (running) {
            rethrowFailure();
        }
        if (!running) {
            backend.step();
            phase = Phase.OBSERVE;
            return;
        }
        if (phase == Phase.OBSERVE) {
            drive();
        }

        int observedGeneration = stepGeneration;
        stepArrivals++;
        releaseStepIfReady();
        while (observedGeneration == stepGeneration && failure == null) {
            awaitPhaseChange();
        }
        rethrowFailure();
    }

    private void stepBackend() {
        backend.step();
        phase = Phase.OBSERVE;
        stepGeneration++;
    }

    synchronized long readPort(String path) {
        checkRead();
        return backend.read(path);
    }

    synchronized BigInteger readBigIntegerPort(String path) {
        checkRead();
        return backend.readBigInteger(path);
    }

    private void checkRead() {
        requireAgent();
        if (running) {
            rethrowFailure();
        }
        if (phase == Phase.DRIVE) {
            throw new IllegalStateException("port reads are not allowed during the drive phase");
        }
    }

    void setPort(String path, BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("port values must be unsigned");
        }
        drive();
        synchronized (this) {
            if (running) {
                rethrowFailure();
            }
            backend.write(path, value);
        }
    }

    void setPort(String path, long value) {
        drive();
        synchronized (this) {
            if (running) {
                rethrowFailure();
            }
            backend.write(path, value);
        }
    }

    public void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + " at t=" + time());
        }
    }

    private synchronized void startRun() {
        if (running) {
            throw new IllegalStateException("nested Sim.run(...) is not supported");
        }
        running = true;
        phase = Phase.OBSERVE;
        activeAgents = 1;
        driveArrivals = 0;
        stepArrivals = 0;
        stepGeneration = 0;
        failure = null;
        forkedThreads.clear();
        agents.clear();
        agents.add(Thread.currentThread());
    }

    private void runForkedAgent(Runnable body) {
        try {
            rethrowFailure();
            body.run();
        } catch (Throwable t) {
            recordFailure(t);
        } finally {
            deregisterAgent();
        }
    }

    private synchronized void deregisterAgent() {
        agents.remove(Thread.currentThread());
        activeAgents--;
        if (activeAgents < 0) {
            activeAgents = 0;
            throw new IllegalStateException("too many Doppio agents deregistered");
        }
        releaseDrivePhaseIfReady();
        releaseStepIfReady();
        notifyAll();
    }

    private void releaseDrivePhaseIfReady() {
        if (failure == null && phase == Phase.OBSERVE && activeAgents > 0 && driveArrivals >= activeAgents) {
            driveArrivals = 0;
            phase = Phase.DRIVE;
            notifyAll();
        }
    }

    private void releaseStepIfReady() {
        if (failure == null && activeAgents > 0 && stepArrivals >= activeAgents) {
            stepArrivals = 0;
            driveArrivals = 0;
            try {
                stepBackend();
            } catch (Throwable t) {
                recordFailure(t);
            }
            notifyAll();
        }
    }

    private void awaitPhaseChange() {
        try {
            wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for Doppio agents", e);
        }
    }

    private synchronized void recordFailure(Throwable t) {
        if (failure == null) {
            failure = t;
            interruptForkedThreads();
        }
        notifyAll();
    }

    private void joinForkedThreads() {
        int joined = 0;
        boolean interrupted = Thread.interrupted();
        if (interrupted) {
            recordFailure(new InterruptedException("interrupted while running Doppio agents"));
            interruptForkedThreads();
        }
        try {
            while (true) {
                Thread thread;
                synchronized (this) {
                    if (joined >= forkedThreads.size()) {
                        return;
                    }
                    thread = forkedThreads.get(joined);
                }
                try {
                    thread.join();
                    joined++;
                } catch (InterruptedException e) {
                    interrupted = true;
                    recordFailure(e);
                    interruptForkedThreads();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private synchronized void interruptForkedThreads() {
        for (Thread forked : forkedThreads) {
            if (forked != Thread.currentThread()) {
                forked.interrupt();
            }
        }
    }

    private synchronized void finishRun() {
        running = false;
        activeAgents = 0;
        driveArrivals = 0;
        stepArrivals = 0;
        phase = Phase.OBSERVE;
        agents.clear();
        notifyAll();
    }

    private void requireAgent() {
        if (running && !agents.contains(Thread.currentThread())) {
            throw new IllegalStateException("simulation access requires an agent registered with run or fork");
        }
    }

    private void rethrowFailure() {
        Throwable currentFailure;
        synchronized (this) {
            currentFailure = failure;
        }
        if (currentFailure == null) {
            return;
        }
        if (currentFailure instanceof RuntimeException) {
            throw (RuntimeException) currentFailure;
        }
        if (currentFailure instanceof Error) {
            throw (Error) currentFailure;
        }
        throw new RuntimeException(currentFailure);
    }

    private enum Phase {
        OBSERVE,
        DRIVE
    }
}
