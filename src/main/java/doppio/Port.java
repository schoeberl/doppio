
package doppio;

/**
 * Abstract base class for a port (input or output).
 */

public abstract class Port {
    public enum PortDirection { INPUT, OUTPUT }

    protected int value;
    protected final int width;

    public Port(int width) {
        this.width = width;
        this.value = 0;
    }

    public int get() {
        return value;
    }

    public int getWidth() {
        return width;
    }

    public abstract PortDirection getDirection();
}


