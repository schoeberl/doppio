package doppio.examples;

import doppio.HardwareTest;
import doppio.Signal;
import doppio.Sim;

public final class CounterTest {
    @HardwareTest
    public void resetAndCount(Sim dut) {
        Signal rst = dut.signal("rst");
        Signal count = dut.signal("count");

        dut.cycle(cycle -> {
            dut.expect(count.asLong() == 0, "counter starts at zero");
            cycle.write(() -> rst.set(1));
        });

        dut.cycle(cycle -> {
            dut.expect(count.asLong() == 0, "reset should clear count");
            cycle.write(() -> rst.set(0));
        });

        for (int i = 1; i < 4; i++) {
            dut.cycle(cycle -> {
            });
        }

        dut.expect(count.asLong() == 4, "counter should advance after reset");
    }
}
