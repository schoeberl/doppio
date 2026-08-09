package doppio;

import doppio.backend.SimulatorBackend;
import java.util.function.Consumer;

public final class Sim {
    private final SimulatorBackend backend;

    public Sim(SimulatorBackend backend) {
        this.backend = backend;
    }

    public Signal signal(String path) {
        return new Signal(this, path);
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

    long readSignal(String path) {
        return backend.read(path);
    }

    void setSignal(String path, long value) {
        backend.write(path, value);
    }

    public void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + " at t=" + time());
        }
    }
}
