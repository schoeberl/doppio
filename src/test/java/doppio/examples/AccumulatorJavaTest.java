package doppio.examples;

import doppio.HardwareTest;
import doppio.Signal;
import doppio.Sim;

public final class AccumulatorJavaTest {
    @HardwareTest
    public void accumulatesAndResets(Sim dut) {
        Signal rst = dut.signal("rst");
        Signal en = dut.signal("en");
        Signal in = dut.signal("in");
        Signal out = dut.signal("out");

        dut.cycle(cycle -> {
            cycle.write(() -> {
                rst.set(1);
                en.set(0);
                in.set(0);
            });
        });

        dut.cycle(cycle -> {
            dut.expect(out.asLong() == 0, "reset should clear accumulator");
            cycle.write(() -> rst.set(0));
        });

        dut.cycle(cycle -> {
            cycle.write(() -> {
                en.set(1);
                in.set(1);
            });
        });
        dut.expect(out.asLong() == 1, "accumulator should include first input");

        dut.cycle(cycle -> cycle.write(() -> in.set(2)));
        dut.expect(out.asLong() == 3, "accumulator should include second input");

        dut.cycle(cycle -> cycle.write(() -> in.set(3)));
        dut.expect(out.asLong() == 6, "accumulator should include third input");

        dut.cycle(cycle -> cycle.write(() -> {
            en.set(0);
            in.set(7);
        }));
        dut.expect(out.asLong() == 6, "disabled accumulator should hold its value");

        dut.cycle(cycle -> cycle.write(() -> rst.set(1)));
        dut.expect(out.asLong() == 0, "reset should clear accumulator again");
    }
}
