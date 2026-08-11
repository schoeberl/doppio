package doppio;

import doppio.backend.SimulatorBackend;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Sim {
    private final SimulatorBackend backend;
    private Phase phase = Phase.OBSERVE;
    private final List<Thread> forkedThreads = new ArrayList<>();
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
        Thread thread;
        synchronized (this) {
            if (!running) {
                throw new IllegalStateException("fork is only allowed inside Sim.run(...)");
            }
            activeAgents++;
            thread = new Thread(() -> runForkedAgent(body), "doppio-agent-" + forkedThreads.size());
            forkedThreads.add(thread);
        }
        thread.start();
    }

    public synchronized void drive() {
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
        if (phase == Phase.DRIVE) {
            throw new IllegalStateException("port reads are not allowed during the drive phase");
        }
        return backend.read(path);
    }

    void setPort(String path, long value) {
        drive();
        synchronized (this) {
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
    }

    private void runForkedAgent(Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            recordFailure(t);
        } finally {
            deregisterAgent();
        }
    }

    private synchronized void deregisterAgent() {
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
            stepBackend();
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
        }
        notifyAll();
    }

    private void joinForkedThreads() {
        int joined = 0;
        while (true) {
            Thread thread;
            synchronized (this) {
                if (joined >= forkedThreads.size()) {
                    if (activeAgents == 0) {
                        return;
                    }
                    awaitPhaseChange();
                    continue;
                }
                thread = forkedThreads.get(joined);
                joined++;
            }
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordFailure(e);
                return;
            }
        }
    }

    private synchronized void finishRun() {
        running = false;
        activeAgents = 0;
        driveArrivals = 0;
        stepArrivals = 0;
        phase = Phase.OBSERVE;
        notifyAll();
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
