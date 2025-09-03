package doppio;

/**
 * A simple 1-bit register with clock and synchronous reset.
 */
public class Register extends Module {
    public final InputPort d;
    public final InputPort clk;
    public final InputPort rst;
    public final OutputPort q;

    private int prevClk;

    public Register() {
        this.d = new InputPort(1);
        this.clk = new InputPort(1);
        this.rst = new InputPort(1);
        this.q = new OutputPort(1);
        this.prevClk = 0;
    }

    @Override
    public void eval() {
        // Rising edge detection
        if (prevClk == 0 && clk.get() == 1) {
            if (rst.get() == 1) {
                q.set(0);
            } else {
                q.set(d.get());
            }
        }
        prevClk = clk.get();
    }
}
