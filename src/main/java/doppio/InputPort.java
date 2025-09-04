package doppio;

import java.math.BigInteger;

public class InputPort extends Port {
    public InputPort(int width) {
        super(width);
    }


    /**
     * Set value from the testbench (legal for input ports).
     */
    public void setFromTestbench(BigInteger value) {
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        this.value = value.and(mask);
    }

    /**
     * Setting from DUT is not allowed for input ports.
     */
    public void setFromDut(BigInteger value) {
        throw new UnsupportedOperationException("Cannot set input port from DUT");
    }

    @Override
    public BigInteger getFromDut() {
        return value;
    }

    @Override
    public BigInteger getFromTestbench() {
        throw new UnsupportedOperationException("Cannot get input port value from testbench");
    }

    @Override
    public PortDirection getDirection() {
        return Port.PortDirection.INPUT;
    }
}
