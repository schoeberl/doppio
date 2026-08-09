package doppio.backend;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record VerilatorConfig(
        Path topFile,
        String topModule,
        List<Path> includeDirs,
        Path buildDir,
        boolean traceEnabled,
        String clockSignal,
        List<Signal> signals,
        String verilatorCommand) {

    public VerilatorConfig(
            Path topFile,
            String topModule,
            List<Path> includeDirs,
            Path buildDir,
            boolean traceEnabled,
            String clockSignal,
            List<Signal> signals) {
        this(topFile, topModule, includeDirs, buildDir, traceEnabled, clockSignal, signals, "verilator");
    }

    public VerilatorConfig(
            Path topFile,
            String topModule,
            List<Path> includeDirs,
            Path buildDir,
            boolean traceEnabled) {
        this(topFile, topModule, includeDirs, buildDir, traceEnabled, "clk", List.of(), "verilator");
    }

    public VerilatorConfig {
        Objects.requireNonNull(topFile, "topFile");
        Objects.requireNonNull(topModule, "topModule");
        Objects.requireNonNull(includeDirs, "includeDirs");
        Objects.requireNonNull(buildDir, "buildDir");
        Objects.requireNonNull(clockSignal, "clockSignal");
        Objects.requireNonNull(signals, "signals");
        Objects.requireNonNull(verilatorCommand, "verilatorCommand");
        includeDirs = List.copyOf(includeDirs);
        signals = List.copyOf(signals);
    }

    public static Signal input(String name) {
        return new Signal(name, Direction.INPUT);
    }

    public static Signal output(String name) {
        return new Signal(name, Direction.OUTPUT);
    }

    public enum Direction {
        INPUT,
        OUTPUT
    }

    public record Signal(String name, Direction direction) {
        public Signal {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(direction, "direction");
        }
    }
}
