

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

    public BigInteger get() {
        return value;
    }

    public int getWidth() {
        return width;
    }

    public abstract PortDirection getDirection();
}


