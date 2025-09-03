package doppio;

public class InputPort extends Port {
    public InputPort(int width) {
        super(width);
    }

    public void set(int value) {
        this.value = value & ((1 << width) - 1);
    }

    @Override
    public PortDirection getDirection() {
        return Port.PortDirection.INPUT;
    }
}
