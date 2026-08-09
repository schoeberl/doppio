package doppio.examples;

import doppio.Signal;
import doppio.Sim;

public final class AccumulatorTest {
    public void run(Sim dut) {
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
