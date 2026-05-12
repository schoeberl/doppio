package doppio;

import java.util.List;
import doppio.backend.InMemoryBackend;
import doppio.examples.CounterTest;

public final class CycleTest {
    public void runsCounterExample() {
        TestRunner runner = new TestRunner(testName -> InMemoryBackend.withClockedCounter());
        List<TestResult> results = runner.run(CounterTest.class);

        if (results.size() != 1) {
            throw new AssertionError("expected one result");
        }
        TestResult result = results.get(0);
        if (!result.passed()) {
            throw new AssertionError(result.failure());
        }
    }
}
