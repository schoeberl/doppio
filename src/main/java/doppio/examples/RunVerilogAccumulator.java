package doppio.examples;

import java.nio.file.Path;
import java.util.List;
import doppio.Sim;
import doppio.backend.VerilatorBackend;
import doppio.backend.VerilatorConfig;

public final class RunVerilogAccumulator {
    private RunVerilogAccumulator() {
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
            new AccumulatorTest().run(new Sim(backend));
            System.out.println("PASS AccumulatorTest");
        }
    }
}
