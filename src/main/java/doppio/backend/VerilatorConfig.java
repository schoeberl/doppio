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
        List<ConfigPort> ports,
        String verilatorCommand) {

    public VerilatorConfig(
            Path topFile,
            String topModule,
            List<Path> includeDirs,
            Path buildDir,
            boolean traceEnabled,
            String clockPort,
            List<ConfigPort> ports) {
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

    public static InPort input(String name) {
        return new InPort(name);
    }

    public static OutPort output(String name) {
        return new OutPort(name);
    }

    public enum Direction {
        INPUT,
        OUTPUT
    }

    public interface ConfigPort {
        String name();

        Direction direction();
    }

    public record InPort(String name) implements ConfigPort {
        public InPort {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public Direction direction() {
            return Direction.INPUT;
        }
    }

    public record OutPort(String name) implements ConfigPort {
        public OutPort {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public Direction direction() {
            return Direction.OUTPUT;
        }
    }
}
