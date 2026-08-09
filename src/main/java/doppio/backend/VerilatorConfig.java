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
        String clockPort,
        List<Port> ports,
        String verilatorCommand) {

    public VerilatorConfig(
            Path topFile,
            String topModule,
            List<Path> includeDirs,
            Path buildDir,
            boolean traceEnabled,
            String clockPort,
            List<Port> ports) {
        this(topFile, topModule, includeDirs, buildDir, traceEnabled, clockPort, ports, "verilator");
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
        Objects.requireNonNull(clockPort, "clockPort");
        Objects.requireNonNull(ports, "ports");
        Objects.requireNonNull(verilatorCommand, "verilatorCommand");
        includeDirs = List.copyOf(includeDirs);
        ports = List.copyOf(ports);
    }

    public static Port input(String name) {
        return new Port(name, Direction.INPUT);
    }

    public static Port output(String name) {
        return new Port(name, Direction.OUTPUT);
    }

    public enum Direction {
        INPUT,
        OUTPUT
    }

    public record Port(String name, Direction direction) {
        public Port {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(direction, "direction");
        }
    }
}
