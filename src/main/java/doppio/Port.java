

package doppio;
import java.math.BigInteger;

/**
 * Abstract base class for a port (input or output).
 */

public abstract class Port {
    public enum PortDirection { INPUT, OUTPUT }

    protected BigInteger value;
    protected final int width;

    public Port(int width) {
        this.width = width;
    this.value = BigInteger.ZERO;
    }


    /**
     * Get value as seen by the DUT (for input ports) or as driven by the DUT (for output ports).
     */
    public abstract BigInteger getFromDut();

    /**
     * Get value as seen by the testbench (for output ports) or as driven by the testbench (for input ports).
     */
    public abstract BigInteger getFromTestbench();

    public int getWidth() {
        return width;
    }

    public abstract PortDirection getDirection();
}


