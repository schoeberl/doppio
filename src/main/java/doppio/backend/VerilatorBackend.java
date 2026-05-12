package doppio.backend;

public final class VerilatorBackend implements SimulatorBackend {
    public VerilatorBackend(VerilatorConfig config) {
        throw new UnsupportedOperationException(
                "Verilator backend is the next integration layer; implement SimulatorBackend here");
    }

    @Override
    public long time() {
        throw unsupported();
    }

    @Override
    public long read(String signalPath) {
        throw unsupported();
    }

    @Override
    public void write(String signalPath, long value) {
        throw unsupported();
    }

    @Override
    public long previous(String signalPath) {
        throw unsupported();
    }

    @Override
    public void step() {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Verilator backend is not implemented yet");
    }
}
