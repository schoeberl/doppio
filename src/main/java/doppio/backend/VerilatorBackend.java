package doppio.backend;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class VerilatorBackend implements SimulatorBackend, AutoCloseable {
    private final VerilatorConfig config;
    private final Map<String, VerilatorConfig.Signal> signals;
    private final Map<String, Long> previousValues = new HashMap<>();
    private final Process simulation;
    private final BufferedWriter commands;
    private final BufferedReader responses;
    private long currentTime;

    public VerilatorBackend(VerilatorConfig config) {
        this.config = config;
        this.signals = indexSignals(config);
        try {
            Path executable = buildSimulation();
            simulation = new ProcessBuilder(executable.toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            commands = new BufferedWriter(new OutputStreamWriter(simulation.getOutputStream(), StandardCharsets.UTF_8));
            responses = new BufferedReader(new InputStreamReader(simulation.getInputStream(), StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("failed to start Verilator simulation", e);
        } catch (IOException e) {
            throw new IllegalStateException("failed to start Verilator simulation", e);
        }
    }

    @Override
    public long time() {
        return currentTime;
    }

    @Override
    public long read(String signalPath) {
        requireSignal(signalPath);
        return command("read " + signalPath);
    }

    @Override
    public void write(String signalPath, long value) {
        VerilatorConfig.Signal signal = requireSignal(signalPath);
        if (signal.direction() == VerilatorConfig.Direction.OUTPUT) {
            throw new IllegalArgumentException("cannot write output signal: " + signalPath);
        }
        command("write " + signalPath + " " + Long.toUnsignedString(value));
    }

    @Override
    public long previous(String signalPath) {
        requireSignal(signalPath);
        return previousValues.getOrDefault(signalPath, 0L);
    }

    @Override
    public void step() {
        previousValues.clear();
        for (String signal : signals.keySet()) {
            previousValues.put(signal, read(signal));
        }
        command("step");
        currentTime++;
    }

    @Override
    public void close() {
        try {
            if (simulation.isAlive()) {
                command("finish");
                simulation.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            simulation.destroyForcibly();
        }
    }

    private Path buildSimulation() throws IOException, InterruptedException {
        Path buildDir = config.buildDir().toAbsolutePath().normalize();
        Path objectDir = buildDir.resolve("obj_dir");
        Files.createDirectories(buildDir);
        Path harness = buildDir.resolve("doppio_verilator_main.cpp");
        Files.writeString(harness, harnessSource(), StandardCharsets.UTF_8);

        List<String> command = new ArrayList<>();
        command.add(config.verilatorCommand());
        command.add("--cc");
        command.add("--exe");
        command.add("--build");
        command.add("-Mdir");
        command.add(objectDir.toString());
        command.add("--top-module");
        command.add(config.topModule());
        if (config.traceEnabled()) {
            command.add("--trace");
        }
        for (Path includeDir : config.includeDirs()) {
            command.add("-I" + includeDir.toAbsolutePath().normalize());
        }
        command.add(config.topFile().toAbsolutePath().normalize().toString());
        command.add(harness.toString());

        Process build = new ProcessBuilder(command)
                .directory(buildDir.toFile())
                .redirectErrorStream(true)
                .start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(build.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        int exit = build.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Verilator build failed:\n" + output);
        }

        Path executable = objectDir.resolve("V" + config.topModule());
        if (!Files.isExecutable(executable)) {
            throw new IllegalStateException("Verilator did not produce executable: " + executable);
        }
        return executable;
    }

    private long command(String command) {
        try {
            commands.write(command);
            commands.newLine();
            commands.flush();
            String response = responses.readLine();
            if (response == null) {
                throw new IllegalStateException("Verilator simulation exited while handling command: " + command);
            }
            if (response.equals("OK")) {
                return 0L;
            }
            if (response.startsWith("OK ")) {
                return Long.parseUnsignedLong(response.substring(3));
            }
            if (response.startsWith("ERR ")) {
                throw new IllegalStateException(response.substring(4));
            }
            throw new IllegalStateException("unexpected Verilator response: " + response);
        } catch (IOException e) {
            throw new IllegalStateException("failed to communicate with Verilator simulation", e);
        }
    }

    private VerilatorConfig.Signal requireSignal(String signalPath) {
        VerilatorConfig.Signal signal = signals.get(signalPath);
        if (signal == null) {
            throw new IllegalArgumentException("unknown Verilator signal: " + signalPath);
        }
        return signal;
    }

    private static Map<String, VerilatorConfig.Signal> indexSignals(VerilatorConfig config) {
        Map<String, VerilatorConfig.Signal> indexed = new HashMap<>();
        for (VerilatorConfig.Signal signal : config.signals()) {
            VerilatorConfig.Signal previous = indexed.put(signal.name(), signal);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate Verilator signal: " + signal.name());
            }
        }
        if (indexed.isEmpty()) {
            throw new IllegalArgumentException("VerilatorConfig must include at least one signal");
        }
        if (indexed.containsKey(config.clockSignal())) {
            throw new IllegalArgumentException("clock signal is controlled by the backend: " + config.clockSignal());
        }
        return Map.copyOf(indexed);
    }

    private String harnessSource() {
        String topClass = "V" + config.topModule();
        StringBuilder source = new StringBuilder();
        source.append("#include \"").append(topClass).append(".h\"\n");
        source.append("#include \"verilated.h\"\n");
        source.append("#include <cstdint>\n");
        source.append("#include <iostream>\n");
        source.append("#include <string>\n\n");
        source.append("static ").append(topClass).append("* top = nullptr;\n\n");
        appendReadFunction(source);
        appendWriteFunction(source);
        appendMain(source, topClass);
        return source.toString();
    }

    private void appendReadFunction(StringBuilder source) {
        source.append("static std::uint64_t read_port(const std::string& name) {\n");
        for (VerilatorConfig.Signal signal : config.signals()) {
            source.append("    if (name == \"").append(cppString(signal.name())).append("\") {\n");
            source.append("        return static_cast<std::uint64_t>(top->").append(cppIdentifier(signal.name())).append(");\n");
            source.append("    }\n");
        }
        source.append("    return 0;\n");
        source.append("}\n\n");
    }

    private void appendWriteFunction(StringBuilder source) {
        source.append("static bool write_port(const std::string& name, std::uint64_t value) {\n");
        for (VerilatorConfig.Signal signal : config.signals()) {
            if (signal.direction() == VerilatorConfig.Direction.INPUT) {
                source.append("    if (name == \"").append(cppString(signal.name())).append("\") {\n");
                source.append("        top->").append(cppIdentifier(signal.name())).append(" = value;\n");
                source.append("        top->eval();\n");
                source.append("        return true;\n");
                source.append("    }\n");
            }
        }
        source.append("    return false;\n");
        source.append("}\n\n");
    }

    private void appendMain(StringBuilder source, String topClass) {
        source.append("int main(int argc, char** argv) {\n");
        source.append("    Verilated::commandArgs(argc, argv);\n");
        source.append("    ").append(topClass).append(" model;\n");
        source.append("    top = &model;\n");
        source.append("    top->").append(cppIdentifier(config.clockSignal())).append(" = 0;\n");
        source.append("    top->eval();\n");
        source.append("    std::string command;\n");
        source.append("    while (std::cin >> command) {\n");
        source.append("        if (command == \"read\") {\n");
        source.append("            std::string name;\n");
        source.append("            std::cin >> name;\n");
        source.append("            std::cout << \"OK \" << read_port(name) << std::endl;\n");
        source.append("        } else if (command == \"write\") {\n");
        source.append("            std::string name;\n");
        source.append("            std::uint64_t value;\n");
        source.append("            std::cin >> name >> value;\n");
        source.append("            if (write_port(name, value)) {\n");
        source.append("                std::cout << \"OK\" << std::endl;\n");
        source.append("            } else {\n");
        source.append("                std::cout << \"ERR unknown or read-only signal: \" << name << std::endl;\n");
        source.append("            }\n");
        source.append("        } else if (command == \"step\") {\n");
        source.append("            top->").append(cppIdentifier(config.clockSignal())).append(" = 0;\n");
        source.append("            top->eval();\n");
        source.append("            top->").append(cppIdentifier(config.clockSignal())).append(" = 1;\n");
        source.append("            top->eval();\n");
        source.append("            top->").append(cppIdentifier(config.clockSignal())).append(" = 0;\n");
        source.append("            top->eval();\n");
        source.append("            std::cout << \"OK\" << std::endl;\n");
        source.append("        } else if (command == \"finish\") {\n");
        source.append("            top->final();\n");
        source.append("            std::cout << \"OK\" << std::endl;\n");
        source.append("            return 0;\n");
        source.append("        } else {\n");
        source.append("            std::cout << \"ERR unknown command: \" << command << std::endl;\n");
        source.append("        }\n");
        source.append("    }\n");
        source.append("    return 0;\n");
        source.append("}\n");
    }

    private static String cppIdentifier(String name) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("unsupported C++ port identifier: " + name);
        }
        return name;
    }

    private static String cppString(String value) {
        Set<Character> forbidden = Set.of('\n', '\r', '\t', '"', '\\');
        for (int i = 0; i < value.length(); i++) {
            if (forbidden.contains(value.charAt(i))) {
                throw new IllegalArgumentException("unsupported signal name: " + value);
            }
        }
        return value;
    }
}
