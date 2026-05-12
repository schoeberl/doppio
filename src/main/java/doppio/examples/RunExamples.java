package doppio.examples;

import java.util.List;
import doppio.TestResult;
import doppio.TestRunner;
import doppio.backend.InMemoryBackend;

public final class RunExamples {
    private RunExamples() {
    }

    public static void main(String[] args) {
        TestRunner runner = new TestRunner(testName -> InMemoryBackend.withClockedCounter());
        List<TestResult> results = runner.run(CounterTest.class);
        boolean failed = false;
        for (TestResult result : results) {
            if (result.passed()) {
                System.out.println("PASS " + result.name());
            } else {
                failed = true;
                System.out.println("FAIL " + result.name() + ": " + result.failure());
            }
        }
        if (failed) {
            System.exit(1);
        }
    }
}
