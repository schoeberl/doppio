package doppio;

/**
 * Simple testbench base class for digital circuit verification.
 */
public abstract class Testbench<T extends Module> {
    protected final T dut;

    public Testbench(T dut) {
        this.dut = dut;
    }

    public abstract void run();
}
