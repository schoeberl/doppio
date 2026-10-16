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

    public static InPort input(String name, int width) {
        return new InPort(name, width);
    }

    public static OutPort output(String name) {
        return new OutPort(name);
    }

    public static OutPort output(String name, int width) {
        return new OutPort(name, width);
    }

    public Path traceFile() {
        return buildDir.toAbsolutePath().normalize().resolve("waveform.vcd");
    }

    public enum Direction {
        INPUT,
        OUTPUT
    }

    public interface ConfigPort {
        String name();

        Direction direction();

        default int width() {
            return 64;
        }
    }

    public record InPort(String name, int width) implements ConfigPort {
        public InPort(String name) {
            this(name, 64);
        }

        public InPort {
            Objects.requireNonNull(name, "name");
            if (width <= 0) {
                throw new IllegalArgumentException("port width must be positive");
            }
        }

        @Override
        public Direction direction() {
            return Direction.INPUT;
        }
    }

    public record OutPort(String name, int width) implements ConfigPort {
        public OutPort(String name) {
            this(name, 64);
        }

        public OutPort {
            Objects.requireNonNull(name, "name");
            if (width <= 0) {
                throw new IllegalArgumentException("port width must be positive");
            }
        }

        @Override
        public Direction direction() {
            return Direction.OUTPUT;
        }
    }
}
