package doppio.backend;

import java.util.HashMap;
import java.util.Map;

public final class InMemoryBackend implements SimulatorBackend {
    private final Map<String, Long> current = new HashMap<>();
    private final Map<String, Long> previous = new HashMap<>();
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
        return current.getOrDefault(portName, 0L);
    }

    @Override
    public void write(String portName, long value) {
        current.put(portName, value);
    }

    @Override
    public long previous(String portName) {
        return previous.getOrDefault(portName, 0L);
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
