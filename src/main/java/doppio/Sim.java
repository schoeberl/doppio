package doppio;

import doppio.backend.SimulatorBackend;
import java.util.function.Consumer;

public final class Sim {
    private final SimulatorBackend backend;

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
        body.accept(this);
    }

    public void step() {
        backend.step();
    }

    long readPort(String path) {
        return backend.read(path);
    }

    void setPort(String path, long value) {
        backend.write(path, value);
    }

    public void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + " at t=" + time());
        }
    }
}
