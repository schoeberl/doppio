package doppio;

/**
 * Example: A simple AND gate module for demonstration.
 */
public class AndGate extends Module {
    public final InputPort a;
    public final InputPort b;
    public final OutputPort y;

    public AndGate() {
        this.a = new InputPort(1);
        this.b = new InputPort(1);
        this.y = new OutputPort(1);
    }

    @Override
    public void eval() {
        y.set(a.get() & b.get());
    }
}
