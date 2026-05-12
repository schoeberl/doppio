package doppio;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import doppio.backend.SimulatorBackend;

public final class TestRunner {
    private final BackendFactory backendFactory;

    public TestRunner(BackendFactory backendFactory) {
        this.backendFactory = backendFactory;
    }

    public List<TestResult> run(Class<?>... testClasses) {
        List<TestResult> results = new ArrayList<>();
        for (Class<?> testClass : testClasses) {
            runClass(testClass, results);
        }
        return results;
    }

    private void runClass(Class<?> testClass, List<TestResult> results) {
        for (Method method : testClass.getDeclaredMethods()) {
            HardwareTest annotation = method.getAnnotation(HardwareTest.class);
            if (annotation == null) {
                continue;
            }
            String name = annotation.value().isBlank()
                    ? testClass.getSimpleName() + "." + method.getName()
                    : annotation.value();
            results.add(runMethod(testClass, method, name));
        }
    }

    private TestResult runMethod(Class<?> testClass, Method method, String name) {
        try {
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != Sim.class) {
                return TestResult.failed(name, new IllegalStateException("hardware test must take one Sim parameter"));
            }
            if (method.getReturnType() != Void.TYPE) {
                return TestResult.failed(name, new IllegalStateException("hardware test must return void"));
            }

            Object instance = testClass.getDeclaredConstructor().newInstance();
            SimulatorBackend backend = backendFactory.create(name);
            Sim sim = new Sim(backend);
            Object returned = method.invoke(instance, sim);
            return TestResult.passed(name);
        } catch (InvocationTargetException e) {
            return TestResult.failed(name, e.getCause());
        } catch (ReflectiveOperationException | RuntimeException | AssertionError e) {
            return TestResult.failed(name, e);
        }
    }

    @FunctionalInterface
    public interface BackendFactory {
        SimulatorBackend create(String testName);
    }
}
