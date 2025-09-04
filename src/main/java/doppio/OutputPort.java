package doppio;

import java.math.BigInteger;

public class OutputPort extends Port {
    public OutputPort(int width) {
        super(width);
    }


    /**
     * Set value from the DUT (legal for output ports).
     */
    public void setFromDut(BigInteger value) {
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        this.value = value.and(mask);
    }

    /**
     * Setting from testbench is not allowed for output ports.
     */
    public void setFromTestbench(BigInteger value) {
        throw new UnsupportedOperationException("Cannot set output port from testbench");
    }

    @Override
    public BigInteger getFromTestbench() {
        return value;
    }

    @Override
    public BigInteger getFromDut() {
        throw new UnsupportedOperationException("Cannot get output port value from DUT");
    }

    @Override
    public PortDirection getDirection() {
        return Port.PortDirection.OUTPUT;
    }
}
