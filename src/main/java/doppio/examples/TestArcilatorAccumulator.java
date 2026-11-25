package doppio.examples;

import java.nio.file.Path;
import doppio.InPort;
import doppio.OutPort;
import doppio.Sim;
import doppio.backend.arcilator.ArcilatorBackend;
import doppio.backend.arcilator.ArcilatorConfig;

public final class TestArcilatorAccumulator {
    private TestArcilatorAccumulator() {
    }

    public static void main(String[] args) {
        ArcilatorConfig config = new ArcilatorConfig(Path.of("src/mlir/Accumulator.mlir"));

        try (ArcilatorBackend backend = new ArcilatorBackend(config)) {
            testAccumulator(new Sim(backend));
            System.out.println("PASS TestArcilatorAccumulator");
        }
    }

    private static void testAccumulator(Sim dut) {
        dut.run(sim -> {
            InPort reset = sim.inPort("reset");
            InPort en = sim.inPort("en");
            InPort in = sim.inPort("in");
            OutPort out = sim.outPort("out");

            reset.set(1);
            en.set(0);
            in.set(0);
            sim.step();
            sim.expect(out.asLong() == 0, "reset should clear accumulator");

            reset.set(0);
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

            reset.set(1);
            sim.step();
            sim.expect(out.asLong() == 0, "reset should clear accumulator again");
        });
    }
}
