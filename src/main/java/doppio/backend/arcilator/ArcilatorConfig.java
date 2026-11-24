package doppio.backend.arcilator;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ArcilatorConfig(
        Path mlirFile,
        Path buildDir,
        String clockPort,
        List<ConfigPort> ports,
        String arcilatorCommand,
        boolean traceEnabled) {

    public ArcilatorConfig(Path mlirFile, String clockPort, List<ConfigPort> ports) {
        this(mlirFile, defaultBuildDir(mlirFile), clockPort, ports, "arcilator", false);
    }

    public ArcilatorConfig(Path mlirFile, List<ConfigPort> ports) {
        this(mlirFile, defaultBuildDir(mlirFile), "clock", ports, "arcilator", false);
    }

    public ArcilatorConfig(Path mlirFile) {
        this(mlirFile, defaultBuildDir(mlirFile), "clock", List.of(), "arcilator", false);
    }

    public ArcilatorConfig(
            Path mlirFile,
            Path buildDir,
            String clockPort,
            List<ConfigPort> ports) {
        this(mlirFile, buildDir, clockPort, ports, "arcilator", false);
    }

    public ArcilatorConfig(
            Path mlirFile,
            Path buildDir,
            List<ConfigPort> ports) {
        this(mlirFile, buildDir, "clock", ports, "arcilator", false);
    }

    public ArcilatorConfig(
            Path mlirFile,
            Path buildDir,
            String clockPort,
            List<ConfigPort> ports,
            boolean traceEnabled) {
        this(mlirFile, buildDir, clockPort, ports, "arcilator", traceEnabled);
    }

    public ArcilatorConfig {
        Objects.requireNonNull(mlirFile, "mlirFile");
        Objects.requireNonNull(buildDir, "buildDir");
        Objects.requireNonNull(clockPort, "clockPort");
        Objects.requireNonNull(ports, "ports");
        Objects.requireNonNull(arcilatorCommand, "arcilatorCommand");
        ports = List.copyOf(ports);
    }

    private static Path defaultBuildDir(Path mlirFile) {
        Objects.requireNonNull(mlirFile, "mlirFile");
        Path fileName = mlirFile.getFileName();
        String name = fileName == null ? "model" : fileName.toString();
        int extension = name.lastIndexOf('.');
        if (extension > 0) {
            name = name.substring(0, extension);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "model";
        }
        return Path.of("build", "doppio-arcilator", name);
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
        int width();
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
