
package doppio;
import java.math.BigInteger;

/**
 * A simple 1-bit register with clock and synchronous reset.
 */
public class Register extends Module {
    public final InputPort d;
    public final InputPort clk;
    public final InputPort rst;
    public final OutputPort q;

    private BigInteger prevClk;

    public Register() {
        this.d = new InputPort(1);
        this.clk = new InputPort(1);
        this.rst = new InputPort(1);
        this.q = new OutputPort(1);
        this.prevClk = BigInteger.ZERO;
    }

    @Override
    public void eval() {
        // Rising edge detection
        if (prevClk.equals(BigInteger.ZERO) && clk.get().equals(BigInteger.ONE)) {
            if (rst.get().equals(BigInteger.ONE)) {
                q.set(BigInteger.ZERO);
            } else {
                q.set(d.get());
            }
        }
        prevClk = clk.get();
    }
}
