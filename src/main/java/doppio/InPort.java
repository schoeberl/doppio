package doppio;

import java.math.BigInteger;

public final class InPort {
    private final Sim sim;
    private final String path;

    public InPort(Sim sim, String path) {
        this.sim = sim;
        this.path = path;
    }

    public String path() {
        return path;
    }

    public void set(long value) {
        sim.setPort(path, value);
    }

    public void set(BigInteger value) {
        sim.setPort(path, value);
    }

    @Override
    public String toString() {
        return path;
    }
}
