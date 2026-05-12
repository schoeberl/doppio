package doppio;

import doppio.backend.SimulatorBackend;

public final class Sim {
    private final SimulatorBackend backend;
    private Phase phase = Phase.IDLE;

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

    public void cycle(CycleBody body) {
        phase = Phase.READ;
        try {
            body.run(new Cycle(this));
            phase = Phase.IDLE;
            backend.step();
        } finally {
            phase = Phase.IDLE;
        }
    }

    void enterWritePhase() {
        phase = Phase.WRITE;
    }

    long readSignal(String path) {
        if (phase == Phase.WRITE) {
            throw new IllegalStateException("reads are not allowed after cycle.write(...) begins");
        }
        return backend.read(path);
    }

    void writeSignal(String path, long value) {
        if (phase != Phase.WRITE) {
            throw new IllegalStateException("writes are only allowed inside cycle.write(...)");
        }
        backend.write(path, value);
    }

    public void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + " at t=" + time());
        }
    }

    private enum Phase {
        IDLE,
        READ,
        WRITE
    }
}
