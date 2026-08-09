package doppio;

import doppio.backend.InMemoryBackend;
import doppio.examples.CounterTest;

public final class CycleTest {
    public void runsCounterExample() {
        Sim dut = new Sim(InMemoryBackend.withClockedCounter());
        new CounterTest().run(dut);
    }
}
