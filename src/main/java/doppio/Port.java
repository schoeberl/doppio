
package doppio;

/**
 * Abstract base class for a port (input or output).
 */
public abstract class Port {
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
}

/**
 * Represents an input port.
 */
class InputPort extends Port {
    public InputPort(int width) {
        super(width);
    }

    public void set(int value) {
        this.value = value & ((1 << width) - 1);
    }
}

/**
 * Represents an output port.
 */
class OutputPort extends Port {
    public OutputPort(int width) {
        super(width);
    }

    public void set(int value) {
        this.value = value & ((1 << width) - 1);
    }
}
