package doppio.examples;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import doppio.InPort;
import doppio.OutPort;
import doppio.Sim;
import doppio.backend.VerilatorBackend;
import doppio.backend.VerilatorConfig;

public final class TestConcurrentFifo {
    private static final int ITEM_COUNT = 1000;

    private TestConcurrentFifo() {
    }

    public static void main(String[] args) {
        VerilatorConfig config = new VerilatorConfig(
                Path.of("src/verilog/simple_fifo.v"),
                "simple_fifo",
                List.of(Path.of("src/verilog")),
                Path.of("build/doppio-verilator/simple_fifo"),
                false,
                "clk",
                List.of(
                        VerilatorConfig.input("rst"),
                        VerilatorConfig.input("wr_en"),
                        VerilatorConfig.input("rd_en"),
                        VerilatorConfig.input("din"),
                        VerilatorConfig.output("dout"),
                        VerilatorConfig.output("full"),
                        VerilatorConfig.output("empty")));

        try (VerilatorBackend backend = new VerilatorBackend(config)) {
            testConcurrentFifo(new Sim(backend));
        }
    }

    private static void testConcurrentFifo(Sim dut) {
        FifoStats stats = new FifoStats(ITEM_COUNT);

        dut.run(sim -> {
            reset(sim);
            sim.fork(() -> produce(sim, stats));
            sim.fork(() -> consume(sim, stats));
        });

        System.out.printf(Locale.ROOT,
                "PASS TestConcurrentFifo items=%d cycles=%d cycles/item=%.3f average_latency=%.3f cycles%n",
                ITEM_COUNT,
                stats.lastReadCycle(),
                (double) stats.lastReadCycle() / ITEM_COUNT,
                stats.averageLatency());
    }

    private static void reset(Sim sim) {
        InPort rst = sim.inPort("rst");
        InPort wrEn = sim.inPort("wr_en");
        InPort rdEn = sim.inPort("rd_en");
        InPort din = sim.inPort("din");
        OutPort empty = sim.outPort("empty");

        rst.set(1);
        wrEn.set(0);
        rdEn.set(0);
        din.set(0);
        sim.step();
        sim.step();

        sim.expect(empty.isHigh(), "FIFO should be empty after reset");
        rst.set(0);
        sim.step();
    }

    private static void produce(Sim sim, FifoStats stats) {
        InPort wrEn = sim.inPort("wr_en");
        InPort din = sim.inPort("din");
        OutPort full = sim.outPort("full");

        int nextValue = 0;
        while (nextValue < ITEM_COUNT) {
            int acceptedValue = -1;
            if (full.isHigh()) {
                wrEn.set(0);
                din.set(0);
            } else {
                acceptedValue = nextValue;
                din.set(acceptedValue);
                wrEn.set(1);
                nextValue++;
            }
            sim.step();
            if (acceptedValue >= 0) {
                stats.recordWrite(acceptedValue, sim.time());
            }
        }

        wrEn.set(0);
        din.set(0);
        sim.step();
    }

    private static void consume(Sim sim, FifoStats stats) {
        InPort rdEn = sim.inPort("rd_en");
        OutPort empty = sim.outPort("empty");
        OutPort dout = sim.outPort("dout");

        int expectedValue = 0;
        while (expectedValue < ITEM_COUNT) {
            boolean read = !empty.isHigh();
            rdEn.set(read ? 1 : 0);
            sim.step();

            if (read) {
                int actual = (int) dout.asLong();
                sim.expect(actual == expectedValue, String.format(
                        "FIFO read %d, expected %d", actual, expectedValue));
                stats.recordRead(actual, sim.time());
                expectedValue++;
            }
        }

        rdEn.set(0);
        sim.step();
    }

    private static final class FifoStats {
        private final AtomicLongArray writeCycles;
        private long lastReadCycle;
        private long totalLatency;

        private FifoStats(int itemCount) {
            writeCycles = new AtomicLongArray(itemCount);
        }

        private void recordWrite(int value, long cycle) {
            writeCycles.set(value, cycle);
        }

        private synchronized void recordRead(int value, long cycle) {
            long writeCycle = writeCycles.get(value);
            if (writeCycle == 0) {
                throw new AssertionError("read value before recorded write: " + value);
            }
            lastReadCycle = cycle;
            totalLatency += cycle - writeCycle;
        }

        private synchronized long lastReadCycle() {
            return lastReadCycle;
        }

        private synchronized double averageLatency() {
            return (double) totalLatency / ITEM_COUNT;
        }
    }
}
