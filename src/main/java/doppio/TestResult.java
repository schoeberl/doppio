package doppio;

public final class TestResult {
    private final String name;
    private final Throwable failure;

    private TestResult(String name, Throwable failure) {
        this.name = name;
        this.failure = failure;
    }

    public static TestResult passed(String name) {
        return new TestResult(name, null);
    }

    public static TestResult failed(String name, Throwable failure) {
        return new TestResult(name, failure);
    }

    public String name() {
        return name;
    }

    public boolean passed() {
        return failure == null;
    }

    public Throwable failure() {
        return failure;
    }
}
