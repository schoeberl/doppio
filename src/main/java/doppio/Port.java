package doppio;

public final class Port {
    private final Sim sim;
    private final String path;

    public Port(Sim sim, String path) {
        this.sim = sim;
        this.path = path;
    }

    public String path() {
        return path;
    }

    public long asLong() {
        return sim.readPort(path);
    }

    public boolean isHigh() {
        return asLong() != 0;
    }

    public void set(long value) {
        sim.setPort(path, value);
    }

    @Override
    public String toString() {
        return path + "=" + asLong();
    }
}
