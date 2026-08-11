package doppio.examples;

import java.nio.file.Path;
import java.util.List;
import doppio.InPort;
import doppio.OutPort;
import doppio.Sim;
import doppio.backend.VerilatorBackend;
import doppio.backend.VerilatorConfig;

public final class TestAccumulatorWithInterface {
    private TestAccumulatorWithInterface() {
    }

    public static void main(String[] args) {
        VerilatorConfig config = new VerilatorConfig(
                Path.of("src/verilog/accumulator.v"),
                "accumulator",
                List.of(Path.of("src/verilog")),
                Path.of("build/doppio-verilator/accumulator-with-interface"),
                false,
                "clk",
                List.of(
                        VerilatorConfig.input("rst"),
                        VerilatorConfig.input("en"),
                        VerilatorConfig.input("in"),
                        VerilatorConfig.output("out")));

        try (VerilatorBackend backend = new VerilatorBackend(config)) {
            testAccumulator(new Sim(backend));
            System.out.println("PASS TestAccumulatorWithInterface");
        }
    }

    private static void testAccumulator(Sim dut) {
        dut.run(sim -> {
            AccumulatorInterface acc = new AccumulatorInterface(sim);

            acc.reset();
            acc.add(1, 1);
            acc.add(2, 3);
            acc.add(3, 6);
            acc.holdWithInput(7, 6);
            acc.reset();
        });
    }

    private static final class AccumulatorInterface {
        private final Sim sim;
        private final InPort rst;
        private final InPort en;
        private final InPort in;
        private final OutPort out;

        private AccumulatorInterface(Sim sim) {
            this.sim = sim;
            rst = sim.inPort("rst");
            en = sim.inPort("en");
            in = sim.inPort("in");
            out = sim.outPort("out");
        }

        private void reset() {
            rst.set(1);
            en.set(0);
            in.set(0);
            sim.step();
            expectOutput(0, "reset should clear accumulator");

            rst.set(0);
            sim.step();
        }

        private void add(int value, int expected) {
            en.set(1);
            in.set(value);
            sim.step();
            expectOutput(expected, "accumulator should include input " + value);
        }

        private void holdWithInput(int value, int expected) {
            en.set(0);
            in.set(value);
            sim.step();
            expectOutput(expected, "disabled accumulator should hold its value");
        }

        private void expectOutput(int expected, String message) {
            sim.expect(out.asLong() == expected, message);
        }
    }
}
