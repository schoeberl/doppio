package doppio;

import java.math.BigInteger;

public class OutputPort extends Port {
    public OutputPort(int width) {
        super(width);
    }

    public void set(BigInteger value) {
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        this.value = value.and(mask);
    }

    @Override
    public PortDirection getDirection() {
        return Port.PortDirection.OUTPUT;
    }
}
