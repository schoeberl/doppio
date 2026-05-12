package doppio;

public final class Cycle {
    private final Sim sim;

    Cycle(Sim sim) {
        this.sim = sim;
    }

    public Sim sim() {
        return sim;
    }

    public void write(Runnable writes) {
        sim.enterWritePhase();
        writes.run();
    }
}
