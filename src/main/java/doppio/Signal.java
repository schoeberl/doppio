package doppio;

public final class Signal {
    private final Sim sim;
    private final String path;

    public Signal(Sim sim, String path) {
        this.sim = sim;
        this.path = path;
    }

    public String path() {
        return path;
    }

    public long asLong() {
        return sim.readSignal(path);
    }

    public boolean isHigh() {
        return asLong() != 0;
    }

    public void set(long value) {
        sim.writeSignal(path, value);
    }

    @Override
    public String toString() {
        return path + "=" + asLong();
    }
}
