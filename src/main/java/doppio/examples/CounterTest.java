package doppio.examples;

import doppio.InPort;
import doppio.OutPort;
import doppio.Sim;

public final class CounterTest {
    public void run(Sim dut) {
        dut.run(sim -> {
            InPort rst = sim.inPort("rst");
            OutPort count = sim.outPort("count");

            sim.expect(count.asLong() == 0, "counter starts at zero");
            rst.set(1);
            sim.step();

            sim.expect(count.asLong() == 0, "reset should clear count");
            rst.set(0);
            sim.step();

            for (int i = 1; i < 4; i++) {
                sim.step();
            }

            sim.expect(count.asLong() == 4, "counter should advance after reset");
        });
    }
}
