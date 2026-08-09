package doppio.examples;

import doppio.Sim;
import doppio.backend.InMemoryBackend;

public final class RunExamples {
    private RunExamples() {
    }

    public static void main(String[] args) {
        Sim dut = new Sim(InMemoryBackend.withClockedCounter());
        new CounterTest().run(dut);
        System.out.println("PASS CounterTest");
    }
}
