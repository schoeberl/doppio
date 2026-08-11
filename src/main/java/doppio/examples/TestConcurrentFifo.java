package doppio.examples;

import java.nio.file.Path;
import java.util.List;
import doppio.InPort;
import doppio.OutPort;
import doppio.Sim;
import doppio.backend.VerilatorBackend;
import doppio.backend.VerilatorConfig;

public final class TestConcurrentFifo {
    private static final int[] DATA = {
            0x12, 0x34, 0x56, 0x78,
            0x9a, 0xbc, 0xde, 0xf0,
            0x11, 0x22, 0x33, 0x44
    };

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
            System.out.println("PASS TestConcurrentFifo");
        }
    }

    private static void testConcurrentFifo(Sim dut) {
        dut.run(sim -> {
            reset(sim);
            sim.fork(() -> produce(sim));
            sim.fork(() -> consume(sim));
        });
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

    private static void produce(Sim sim) {
        InPort wrEn = sim.inPort("wr_en");
        InPort din = sim.inPort("din");
        OutPort full = sim.outPort("full");

        for (int value : DATA) {
            boolean written = false;
            while (!written) {
                if (full.isHigh()) {
                    wrEn.set(0);
                    din.set(0);
                } else {
                    din.set(value);
                    wrEn.set(1);
                    written = true;
                }
                sim.step();
            }
        }

        wrEn.set(0);
        din.set(0);
        sim.step();
    }

    private static void consume(Sim sim) {
        InPort rdEn = sim.inPort("rd_en");
        OutPort empty = sim.outPort("empty");
        OutPort dout = sim.outPort("dout");

        for (int i = 0; i < 6; i++) {
            rdEn.set(0);
            sim.step();
        }

        for (int expected : DATA) {
            boolean read = false;
            while (!read) {
                if (empty.isHigh()) {
                    rdEn.set(0);
                } else {
                    rdEn.set(1);
                    read = true;
                }
                sim.step();
            }

            long actual = dout.asLong() & 0xff;
            sim.expect(actual == expected, String.format(
                    "FIFO read 0x%02x, expected 0x%02x", actual, expected));
        }

        rdEn.set(0);
        sim.step();
    }
}
