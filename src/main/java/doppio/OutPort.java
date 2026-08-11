package doppio;

public final class OutPort {
    private final Sim sim;
    private final String path;

    public OutPort(Sim sim, String path) {
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

    @Override
    public String toString() {
        return path + "=" + asLong();
    }
}
