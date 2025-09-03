package doppio;

/**
 * Simple clock generator for simulation.
 */
public class Clock {
    private final OutputPort clk;
    private final int period;
    private int time;

    public Clock(OutputPort clk, int period) {
        this.clk = clk;
        this.period = period;
        this.time = 0;
    }

    public void tick() {
        time++;
        if ((time % period) < (period / 2)) {
            clk.set(1);
        } else {
            clk.set(0);
        }
    }
}
