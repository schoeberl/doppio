package doppio.backend;

import java.math.BigInteger;

public interface SimulatorBackend {
    long time();

    long read(String portName);

    void write(String portName, long value);

    long previous(String portName);

    void step();

    default BigInteger readBigInteger(String portName) {
        return new BigInteger(Long.toUnsignedString(read(portName)));
    }

    default void write(String portName, BigInteger value) {
        if (value.signum() < 0 || value.bitLength() > 64) {
            throw new IllegalArgumentException("backend only supports unsigned values up to 64 bits");
        }
        write(portName, value.longValue());
    }

    default BigInteger previousBigInteger(String portName) {
        return new BigInteger(Long.toUnsignedString(previous(portName)));
    }
}
