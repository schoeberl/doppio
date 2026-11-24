package doppio.backend;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ArcilatorBackend implements SimulatorBackend, AutoCloseable {
    // Immutable configuration supplied by the caller.
    private final ArcilatorConfig config;
    // Java-side name-to-port metadata.  This is separate from the C++ driver's
    // offsets and is used for validation and API error reporting.
    private final Map<String, ConfigPortState> ports;
    // Values observed before the latest step(), used by previous*().
    private final Map<String, BigInteger> previousValues = new HashMap<>();
    // The persistent native protocol driver compiled at construction time.
    private final Process simulation;
    private final BufferedWriter commands;
    private final BufferedReader responses;
    // Doppio cycle count.  It is maintained by Java rather than decoded from MLIR.
    private long currentTime;

    /**
     * Builds, validates, and starts the native simulation driver.
     *
     * Construction performs all one-time work: Arcilator lowering, state metadata
     * parsing, C++ driver generation, native compilation, and process startup.
     * Subsequent simulation operations only exchange protocol commands.
     */
    public ArcilatorBackend(ArcilatorConfig config) {
        if (config == null) {
            throw new NullPointerException("config");
        }
        if (config.traceEnabled()) {
            throw new IllegalArgumentException(
                    "native Arcilator execution does not support traceEnabled in this backend");
        }
        this.config = config;
        try {
            ModelInfo model = buildSimulation();
            ports = indexPorts(config, model);
            if (!model.inputs.containsKey(config.clockPort())) {
                throw new IllegalArgumentException(
                        "clock port not found in generated MLIR model: " + config.clockPort());
            }
            simulation = startSimulation(model);
            commands = new BufferedWriter(new OutputStreamWriter(
                    simulation.getOutputStream(), StandardCharsets.UTF_8));
            responses = new BufferedReader(new InputStreamReader(
                    simulation.getInputStream(), StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("failed to build/start Arcilator simulation", e);
        } catch (IOException e) {
            throw new IllegalStateException("failed to build/start Arcilator simulation", e);
        }
    }

    /**
     * Returns the number of successful Doppio step() calls made so far.
     */
    @Override
    public long time() {
        return currentTime;
    }

    /**
     * Reads an output and converts it to an unsigned long.
     * Wide values deliberately fail here so callers use readBigInteger().
     */
    @Override
    public long read(String portName) {
        return asLong(readBigInteger(portName));
    }

    /**
     * Reads an output as an arbitrary-width unsigned BigInteger.
     * The C++ process returns the complete value as hexadecimal text.
     */
    @Override
    public BigInteger readBigInteger(String portName) {
        requirePort(portName);
        return command("read " + portName);
    }

    /** Writes an unsigned Java long as the exact unsigned input representation. */
    @Override
    public void write(String portName, long value) {
        write(portName, new BigInteger(Long.toUnsignedString(value)));
    }

    /**
     * Writes an arbitrary-width unsigned value to a configured input port.
     * Direction and configured-width checks happen in Java before a process
     * command is sent, keeping protocol traffic and native state safe.
     */
    @Override
    public void write(String portName, BigInteger value) {
        ConfigPortState port = requirePort(portName);
        if (port.direction != ArcilatorConfig.Direction.INPUT) {
            throw new IllegalArgumentException("cannot write output port: " + portName);
        }
        if (value.signum() < 0 || value.bitLength() > port.width) {
            throw new IllegalArgumentException(
                    "value does not fit unsigned " + port.width + "-bit port: " + portName);
        }
        command("write " + portName + " " + value.toString(16));
    }

    /** Returns a previous value as an unsigned long, with a 64-bit guard. */
    @Override
    public long previous(String portName) {
        return asLong(previousBigInteger(portName));
    }

    /**
     * Returns the configured port value captured immediately before step().
     * Ports never seen by a snapshot default to zero.
     */
    @Override
    public BigInteger previousBigInteger(String portName) {
        requirePort(portName);
        return previousValues.getOrDefault(portName, BigInteger.ZERO);
    }

    /**
     * Implements one Doppio clock cycle.
     *
     * The Java side snapshots every configured port, sends "step" to C++, and
     * advances its cycle count only after the process acknowledges success.
     */
    @Override
    public void step() {
        previousValues.clear();
        for (String port : ports.keySet()) {
            previousValues.put(port, readBigInteger(port));
        }
        command("step");
        currentTime++;
    }

    /**
     * Gracefully asks the persistent native process to deallocate and exit.
     * If an interruption or protocol failure occurs, destruction is still forced
     * in finally so the backend never leaves a running child process behind.
     */
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

    // Shared conversion guard for the two unsigned long read APIs.
    private static long asLong(BigInteger value) {
        if (value.bitLength() > 64) {
            throw new ArithmeticException("port value exceeds 64 bits; use readBigInteger");
        }
        return value.longValue();
    }

    /**
     * Sends one line-oriented protocol command and waits for one response.
     *
     * Commands and responses are deliberately text based:
     * <ul>
     *   <li>OK marks success</li>
     *   <li>OK &lt;hex&gt; carries a value</li>
     *   <li>ERR &lt;message&gt; carries a driver error</li>
     * </ul>
     * Both streams are flushed/read explicitly to keep Java synchronous.
     */
    private BigInteger command(String text) {
        try {
            commands.write(text);
            commands.newLine();
            commands.flush();
            String response = responses.readLine();
            if (response == null) {
                throw new IllegalStateException(
                        "Arcilator simulation exited while handling command: " + text);
            }
            if (response.equals("OK")) {
                return BigInteger.ZERO;
            }
            if (response.startsWith("OK ")) {
                return new BigInteger(response.substring(3), 16);
            }
            if (response.startsWith("ERR ")) {
                throw new IllegalStateException(response.substring(4));
            }
            throw new IllegalStateException("unexpected Arcilator response: " + response);
        } catch (IOException e) {
            throw new IllegalStateException("failed to communicate with Arcilator simulation", e);
        }
    }

    // Centralized port lookup used by read, write, and previous APIs.
    private ConfigPortState requirePort(String portName) {
        ConfigPortState port = ports.get(portName);
        if (port == null) {
            throw new IllegalArgumentException("unknown Arcilator port: " + portName);
        }
        return port;
    }

    /**
     * Starts the already-built executable without embedding protocol state in
     * Java. Diagnostics are inherited while protocol stdin/stdout stay private.
     */
    private Process startSimulation(ModelInfo model) throws IOException {
        Path executable = model.executable;
        if (config.traceEnabled()) {
            throw new IllegalArgumentException(
                    "native Arcilator execution does not support traceEnabled in this backend");
        }
        return new ProcessBuilder(executable.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
    }

    /**
     * Performs the complete Arcilator build pipeline.
     *
     * 1. Generate state/offset metadata as JSON.
     * 2. Lower the MLIR model to LLVM IR.
     * 3. Generate a C++ protocol/port driver in Java.
     * 4. Compile LLVM IR and C++ together into one executable.
     *
     * These artifacts are retained in buildDir for inspection and debugging.
     */
    private ModelInfo buildSimulation()
            throws IOException, InterruptedException {
        // Normalize paths and validate input before invoking any external tool.
        Path mlir = config.mlirFile().toAbsolutePath().normalize();
        if (!Files.isRegularFile(mlir)) {
            throw new IllegalArgumentException("MLIR input file not found: " + mlir);
        }
        // Fixed build artifacts make regeneration and debugging reproducible.
        Path buildDir = config.buildDir().toAbsolutePath().normalize();
        Files.createDirectories(buildDir);
        Path stateFile = buildDir.resolve("doppio-arcilator-state.json");
        Path llvmFile = buildDir.resolve("doppio-arcilator-model.ll");
        Path objectFile = buildDir.resolve("doppio-arcilator-model.o");
        Path harnessFile = buildDir.resolve("doppio-arcilator-main.cpp");
        Path executable = buildDir.resolve("doppio-arcilator");

        // The --state-file and --emit-llvm options let Java discover offsets
        // and widths and compile the lowered model without an ArcRuntime library.
        List<String> arcilatorCommand = new ArrayList<>();
        arcilatorCommand.add(config.arcilatorCommand());
        arcilatorCommand.add("--observe-ports");
        arcilatorCommand.add("--no-runtime");
        arcilatorCommand.add("--state-file=" + stateFile);
        arcilatorCommand.add("--emit-llvm");
        arcilatorCommand.add("-o");
        arcilatorCommand.add(llvmFile.toString());
        arcilatorCommand.add(mlir.toString());
        runBuildCommand(arcilatorCommand, "Arcilator build failed");

        // Parse only after Arcilator succeeds, then generate C++ from metadata.
        ModelInfo model = parseStateFile(stateFile, buildDir);
        Files.writeString(harnessFile,
                harnessSource(model), StandardCharsets.UTF_8);

        // clang++ compiles the generated LLVM object and C++ protocol harness
        // into a standalone child executable.
        runBuildCommand(List.of(
                "clang++", "-std=c++17", "-c", llvmFile.toString(),
                "-o", objectFile.toString()), "Arcilator LLVM compilation failed");
        runBuildCommand(List.of(
                "clang++", "-std=c++17", harnessFile.toString(),
                objectFile.toString(), "-o", executable.toString()),
                "Arcilator driver compilation failed");
        if (!Files.isExecutable(executable)) {
            throw new IllegalStateException(
                    "Arcilator did not produce executable: " + executable);
        }
        model.executable = executable;
        return model;
    }

    /**
     * Runs an external build command with merged diagnostic output.
     * A non-zero exit becomes an IllegalStateException containing tool output.
     */
    private void runBuildCommand(List<String> command, String failurePrefix)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(failurePrefix + ":\n" + output);
        }
    }

    /**
     * Checks the public configuration against Arcilator's generated state
     * metadata.  This is where authoritative Java names and widths are matched
     * to real input/output offsets emitted by the Arc lowering pipeline.
     */
    private static Map<String, ConfigPortState> indexPorts(
            ArcilatorConfig config, ModelInfo model) {
        Map<String, ConfigPortState> result = new HashMap<>();
        if (config.ports().isEmpty()) {
            for (StatePort port : model.inputs.values()) {
                if (!port.name.equals(config.clockPort())) {
                    result.put(port.name, new ConfigPortState(
                            port.name, port.width, ArcilatorConfig.Direction.INPUT));
                }
            }
            for (StatePort port : model.outputs.values()) {
                ConfigPortState previous = result.put(port.name, new ConfigPortState(
                        port.name, port.width, ArcilatorConfig.Direction.OUTPUT));
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "duplicate Arcilator port across directions: " + port.name);
                }
            }
        } else {
            for (ArcilatorConfig.ConfigPort configured : config.ports()) {
                if (configured.name().equals(config.clockPort())) {
                    throw new IllegalArgumentException(
                            "clock port is controlled by the backend: " + configured.name());
                }
                StatePort generated = configured.direction() == ArcilatorConfig.Direction.INPUT
                        ? model.inputs.get(configured.name())
                        : model.outputs.get(configured.name());
                if (generated == null) {
                    throw new IllegalArgumentException(
                            "configured port not found in generated MLIR model: "
                                    + configured.name());
                }
                if (generated.width != configured.width()) {
                    throw new IllegalArgumentException(
                            "configured width " + configured.width()
                                    + " does not match MLIR width " + generated.width
                                    + " for port " + configured.name());
                }
                ConfigPortState previous = result.put(configured.name(),
                        new ConfigPortState(configured.name(), configured.width(),
                                configured.direction()));
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "duplicate Arcilator port: " + configured.name());
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "Arcilator model has no non-clock ports to expose");
        }
        return Map.copyOf(result);
    }

    private String harnessSource(ModelInfo model) {
        // Generated C++ is not a second hardware model.  It allocates a byte
        // array for the exact numStateBytes described by Arcilator and exposes
        // direct byte-offset access to the lowered <ModelName>_eval function.
        StringBuilder source = new StringBuilder();
        source.append("#include <cstdint>\n");
        source.append("#include <cstdlib>\n");
        source.append("#include <cstring>\n");
        source.append("#include <iostream>\n");
        source.append("#include <sstream>\n");
        source.append("#include <iomanip>\n");
        source.append("#include <string>\n\n");
        source.append("extern \"C\" void ").append(model.name).append("_eval(void* state);\n");
        if (!model.initialFn.isEmpty()) {
            source.append("extern \"C\" void ").append(model.initialFn)
                    .append("(void* state);\n");
        }
        source.append("\nstatic uint8_t* state = nullptr;\n");
        // read_bits serializes a potentially wide port as hexadecimal text.
        // The byte array uses the same little-endian layout as the Arc lowering.
        source.append("static bool read_bits(int offset, int width, std::string& hex) {\n");
        source.append("    int bytes = (width + 7) / 8;\n");
        source.append("    std::ostringstream out;\n");
        source.append("    out << std::hex << std::nouppercase;\n");
        source.append("    bool started = false;\n");
        source.append("    for (int i = bytes - 1; i >= 0; --i) {\n");
        source.append("        unsigned char byte = state[offset + i];\n");
        source.append("        if (!started && byte == 0) continue;\n");
        source.append("        if (!started) {\n");
        source.append("            started = true;\n");
        source.append("            if (i == bytes - 1 && (width % 8) != 0)\n");
        source.append("                out << (unsigned)(byte & ((1 << (width % 8)) - 1));\n");
        source.append("            else out << (unsigned)byte;\n");
        source.append("        } else {\n");
        source.append("            out << std::setw(2) << std::setfill('0') << (unsigned)byte;\n");
        source.append("        }\n");
        source.append("    }\n");
        source.append("    hex = out.str();\n");
        source.append("    if (hex.empty()) hex = \"0\";\n");
        source.append("    return true;\n}\n");
        source.append("\nstatic bool read_port(const std::string& name, std::string& hex) {\n");
        // One branch is generated for every input and output state entry.
        for (StatePort port : model.allPorts()) {
            source.append("    if (name == \"").append(cppString(port.name))
                    .append("\") {\n");
            source.append("        return read_bits(").append(port.offset)
                    .append(", ").append(port.width).append(", hex);\n    }\n");
        }
        source.append("    return false;\n}\n");
        source.append("\nstatic bool write_port(const std::string& name, const std::string& value) {\n");
        // Only input entries are emitted here.  An output name therefore
        // naturally reaches the harness's unknown/read-only error response.
        for (StatePort port : model.inputs.values()) {
            source.append("    if (name == \"").append(cppString(port.name)).append("\") {\n");
            source.append("        if (value.find_first_not_of(\"0123456789abcdefABCDEF\") != std::string::npos) return false;\n");
            source.append("        int bytes = (").append(port.width).append(" + 7) / 8;\n");
            source.append("        std::memset(state + ").append(port.offset).append(", 0, bytes);\n");
            source.append("        std::string padded = value;\n");
            source.append("        if (padded.empty()) padded = \"0\";\n");
            source.append("        int hexBytes = (").append(port.width).append(" + 7) / 8;\n");
            source.append("        size_t paddedSize = hexBytes * 2;\n");
            source.append("        if (padded.size() > paddedSize) return false;\n");
            source.append("        padded.insert(padded.begin(), paddedSize - padded.size(), '0');\n");
            source.append("        for (int i = 0; i < hexBytes; ++i) {\n");
            source.append("            size_t pos = padded.size() - 2 * (i + 1);\n");
            source.append("            unsigned char byte = 0;\n");
            source.append("            for (char c : padded.substr(pos, 2)) {\n");
            source.append("                unsigned nibble;\n");
            source.append("                if (c >= '0' && c <= '9') nibble = c - '0';\n");
            source.append("                else nibble = (c >= 'a' && c <= 'f' ? c - 'a' + 10 : c - 'A' + 10);\n");
            source.append("                byte = (byte << 4) | nibble;\n");
            source.append("            }\n");
            source.append("            state[").append(port.offset).append(" + i] = byte;\n");
            source.append("        }\n");
            if (port.width % 8 != 0) {
                int mask = (1 << (port.width % 8)) - 1;
                source.append("        state[").append(port.offset + port.width / 8)
                        .append("] &= ").append(mask).append(";\n");
            }
            source.append("        return true;\n    }\n");
        }
        source.append("    return false;\n}\n");
        source.append("\nstatic bool set_clock(int value) {\n");
        // The clock's generated offset is discovered from --state-file.
        StatePort clock = model.inputs.get(config.clockPort());
        if (clock == null) {
            throw new IllegalArgumentException(
                    "clock port not found in generated MLIR model: " + config.clockPort());
        }
        source.append("    state[").append(clock.offset).append("] = value ? 1 : 0;\n");
        source.append("    return true;\n}\n");
        source.append("\nint main() {\n");
        // Allocate and zero every model state byte.  This replaces reset/init
        // work normally supplied by an Arc runtime allocation.
        source.append("    state = (uint8_t*)std::malloc(").append(model.numStateBytes).append(");\n");
        source.append("    if (!state) return 1;\n");
        source.append("    std::memset(state, 0, ").append(model.numStateBytes).append(");\n");
        if (!model.initialFn.isEmpty()) {
            source.append("    ").append(model.initialFn).append("(state);\n");
        }
        source.append("    ").append(model.name).append("_eval(state);\n");
        source.append("    std::string command;\n");
        // Protocol loop: one command line, one response line, keep process alive.
        source.append("    while (std::cin >> command) {\n");
        source.append("        if (command == \"read\") {\n");
        source.append("            std::string name, hex;\n std::cin >> name;\n");
        source.append("            if (read_port(name, hex)) std::cout << \"OK \" << hex << std::endl;\n");
        source.append("            else std::cout << \"ERR unknown port: \" << name << std::endl;\n");
        source.append("        } else if (command == \"write\") {\n");
        source.append("            std::string name, value;\n std::cin >> name >> value;\n");
        source.append("            if (write_port(name, value)) std::cout << \"OK\" << std::endl;\n");
        source.append("            else std::cout << \"ERR unknown or read-only port: \" << name << std::endl;\n");
        source.append("        } else if (command == \"step\") {\n");
        // Low/high/low is the same synchronous edge convention used by Doppio's
        // Verilator backend.  Evaluation after high captures rising-edge state.
        source.append("            set_clock(0);\n ").append(model.name).append("_eval(state);\n");
        source.append("            set_clock(1);\n ").append(model.name).append("_eval(state);\n");
        source.append("            set_clock(0);\n ").append(model.name).append("_eval(state);\n");
        source.append("            std::cout << \"OK\" << std::endl;\n");
        source.append("        } else if (command == \"finish\") {\n");
        // finish is the only protocol command that closes state and terminates.
        source.append("            std::cout << \"OK\" << std::endl;\n");
        source.append("            std::free(state);\n");
        source.append("            return 0;\n");
        source.append("        } else {\n");
        source.append("            std::cout << \"ERR unknown command: \" << command << std::endl;\n");
        source.append("        }\n");
        source.append("    }\n");
        source.append("    std::free(state);\n");
        source.append("    return 0;\n}\n");
        return source.toString();
    }

    private static ModelInfo parseStateFile(Path stateFile, Path buildDir)
            throws IOException {
        // State JSON is a small internal interchange format.  Java parses the
        // model name, allocation size, initial function, and state entries.
        String json = Files.readString(stateFile, StandardCharsets.UTF_8);
        // The regexes deliberately extract only the fields needed by this backend.
        Pattern modelPattern = Pattern.compile(
                "\\{\\s*\"name\":\\s*\"([^\"]+)\".*?\"numStateBytes\":\\s*(\\d+).*?\"states\":\\s*\\[(.*?)\\]\\s*\\}",
                Pattern.DOTALL);
        Matcher modelMatcher = modelPattern.matcher(json);
        if (!modelMatcher.find()) {
            throw new IllegalStateException("Arcilator state file contains no model");
        }
        String name = modelMatcher.group(1);
        int numStateBytes = Integer.parseInt(modelMatcher.group(2));
        String statesJson = modelMatcher.group(3);

        // initialFnSym is invoked before the first eval if the lowering supplied it.
        Pattern initialPattern = Pattern.compile("\"initialFnSym\":\\s*\"([^\"]*)\"");
        Matcher initialMatcher = initialPattern.matcher(modelMatcher.group(0));
        String initialFn = initialMatcher.find() ? initialMatcher.group(1) : "";

        // input/output entries become separate maps; duplicate wires/registers
        // are irrelevant because they are not directly observable public ports.
        Pattern statePattern = Pattern.compile("\\{([^{}]*)\\}", Pattern.DOTALL);
        Matcher stateMatcher = statePattern.matcher(statesJson);
        Map<String, StatePort> inputs = new HashMap<>();
        Map<String, StatePort> outputs = new HashMap<>();
        while (stateMatcher.find()) {
            String object = stateMatcher.group(1);
            String stateName = stringField(object, "name");
            String type = stringField(object, "type");
            if (stateName == null || type == null) continue;
            int offset = intField(object, "offset");
            int width = intField(object, "numBits");
            if ("input".equals(type)) inputs.put(stateName, new StatePort(stateName, offset, width));
            if ("output".equals(type)) outputs.put(stateName, new StatePort(stateName, offset, width));
        }
        // Empty output metadata is rejected before native compilation.
        if (outputs.isEmpty()) {
            throw new IllegalStateException(
                    "Arcilator model has no observable output ports; use --observe-ports");
        }
        ModelInfo model = new ModelInfo(name, numStateBytes, initialFn, inputs, outputs);
        model.executable = buildDir.resolve("doppio-arcilator");
        return model;
    }

    // Reads a quoted JSON string field without introducing a JSON dependency.
    private static String stringField(String object, String field) {
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(object);
        return matcher.find() ? matcher.group(1) : null;
    }

    // Reads one numeric state metadata field.
    private static int intField(String object, String field) {
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)").matcher(object);
        if (!matcher.find()) {
            throw new IllegalStateException("missing state field: " + field);
        }
        return Integer.parseInt(matcher.group(1));
    }

    // Escapes a name before inserting it into a generated C++ string literal.
    private static String cppString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // StatePort is the Arcilator view of one named state entry.
    private record StatePort(String name, int offset, int width) {
    }

    // ConfigPortState is the Java/public API view after configuration validation.
    private record ConfigPortState(
            String name, int width, ArcilatorConfig.Direction direction) {
    }

    // ModelInfo groups all metadata needed to compile and start the driver.
    private static final class ModelInfo {
        final String name;
        final int numStateBytes;
        final String initialFn;
        final Map<String, StatePort> inputs;
        final Map<String, StatePort> outputs;
        Path executable;

        ModelInfo(String name, int numStateBytes, String initialFn,
                  Map<String, StatePort> inputs, Map<String, StatePort> outputs) {
            this.name = name;
            this.numStateBytes = numStateBytes;
            this.initialFn = initialFn;
            this.inputs = Map.copyOf(inputs);
            this.outputs = Map.copyOf(outputs);
        }

        List<StatePort> allPorts() {
            Map<String, StatePort> all = new HashMap<>(inputs);
            all.putAll(outputs);
            return List.copyOf(all.values());
        }
    }
}
