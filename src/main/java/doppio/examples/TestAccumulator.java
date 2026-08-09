package doppio.examples;

import java.nio.file.Path;
import java.util.List;
import doppio.Signal;
import doppio.Sim;
import doppio.backend.VerilatorBackend;
import doppio.backend.VerilatorConfig;

public final class TestAccumulator {
    private TestAccumulator() {
    }

    public static void main(String[] args) {
        VerilatorConfig config = new VerilatorConfig(
                Path.of("src/verilog/accumulator.v"),
                "accumulator",
                List.of(Path.of("src/verilog")),
                Path.of("build/doppio-verilator/accumulator"),
                false,
                "clk",
                List.of(
                        VerilatorConfig.input("rst"),
                        VerilatorConfig.input("en"),
                        VerilatorConfig.input("in"),
                        VerilatorConfig.output("out")));

        try (VerilatorBackend backend = new VerilatorBackend(config)) {
            runAccumulator(new Sim(backend));
            System.out.println("PASS TestAccumulator");
        }
    }

    private static void runAccumulator(Sim dut) {
        dut.run(sim -> {
            Signal rst = sim.signal("rst");
            Signal en = sim.signal("en");
            Signal in = sim.signal("in");
            Signal out = sim.signal("out");

            rst.set(1);
            en.set(0);
            in.set(0);
            sim.step();

            sim.expect(out.asLong() == 0, "reset should clear accumulator");
            rst.set(0);
            sim.step();

            en.set(1);
            in.set(1);
            sim.step();
            sim.expect(out.asLong() == 1, "accumulator should include first input");

            in.set(2);
            sim.step();
            sim.expect(out.asLong() == 3, "accumulator should include second input");

            in.set(3);
            sim.step();
            sim.expect(out.asLong() == 6, "accumulator should include third input");

            en.set(0);
            in.set(7);
            sim.step();
            sim.expect(out.asLong() == 6, "disabled accumulator should hold its value");

            rst.set(1);
            sim.step();
            sim.expect(out.asLong() == 0, "reset should clear accumulator again");
        });
    }
}
