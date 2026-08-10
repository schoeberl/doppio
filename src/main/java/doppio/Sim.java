package doppio;

import doppio.backend.SimulatorBackend;
import java.util.function.Consumer;

public final class Sim {
    private final SimulatorBackend backend;
    private Phase phase = Phase.OBSERVE;

    public Sim(SimulatorBackend backend) {
        this.backend = backend;
    }

    public Port port(String path) {
        return new Port(this, path);
    }

    public long time() {
        return backend.time();
    }

    public SimulatorBackend backend() {
        return backend;
    }

    public void run(Consumer<Sim> body) {
        phase = Phase.OBSERVE;
        body.accept(this);
    }

    public void drive() {
        phase = Phase.DRIVE;
    }

    public void drive(Runnable drives) {
        drive();
        drives.run();
    }

    public void step() {
        backend.step();
        phase = Phase.OBSERVE;
    }

    long readPort(String path) {
        if (phase == Phase.DRIVE) {
            throw new IllegalStateException("port reads are not allowed during the drive phase");
        }
        return backend.read(path);
    }

    void setPort(String path, long value) {
        drive();
        backend.write(path, value);
    }

    public void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + " at t=" + time());
        }
    }

    private enum Phase {
        OBSERVE,
        DRIVE
    }
}
