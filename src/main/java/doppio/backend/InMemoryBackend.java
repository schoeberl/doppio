package doppio.backend;

import java.util.HashMap;
import java.util.Map;
import java.math.BigInteger;
import java.util.Objects;

public final class InMemoryBackend implements SimulatorBackend {
    private final Map<String, BigInteger> current = new HashMap<>();
    private final Map<String, BigInteger> previous = new HashMap<>();
    private final Tick tick;
    private long time;

    public InMemoryBackend(Tick tick) {
        this.tick = tick;
    }

    public static InMemoryBackend withClockedCounter() {
        InMemoryBackend backend = new InMemoryBackend(b -> {
            if (b.read("rst") != 0) {
                b.write("count", 0);
            } else {
                b.write("count", b.read("count") + 1);
            }
        });
        backend.write("rst", 0);
        backend.write("count", 0);
        return backend;
    }

    @Override
    public long time() {
        return time;
    }

    @Override
    public long read(String portName) {
        return asLong(readBigInteger(portName));
    }

    @Override
    public void write(String portName, long value) {
        write(portName, new BigInteger(Long.toUnsignedString(value)));
    }

    @Override
    public long previous(String portName) {
        return asLong(previousBigInteger(portName));
    }

    @Override
    public BigInteger readBigInteger(String portName) {
        return current.getOrDefault(portName, BigInteger.ZERO);
    }

    @Override
    public BigInteger previousBigInteger(String portName) {
        return previous.getOrDefault(portName, BigInteger.ZERO);
    }

    @Override
    public void write(String portName, BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("port values must be unsigned");
        }
        current.put(portName, value);
    }

    private static long asLong(BigInteger value) {
        if (value.bitLength() > 64) {
            throw new ArithmeticException("port value exceeds 64 bits; use readBigInteger");
        }
        return value.longValue();
    }

    @Override
    public void step() {
        previous.clear();
        previous.putAll(current);
        tick.apply(this);
        time++;
    }

    @FunctionalInterface
    public interface Tick {
        void apply(InMemoryBackend backend);
    }
}
