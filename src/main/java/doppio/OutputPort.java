package doppio;

public class OutputPort extends Port {
    public OutputPort(int width) {
        super(width);
    }

    public void set(int value) {
        this.value = value & ((1 << width) - 1);
    }

    @Override
    public PortDirection getDirection() {
        return Port.PortDirection.OUTPUT;
    }
}
