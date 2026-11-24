# Arcilator Backend Implementation Guide

This document explains the implementation of Doppio's `ArcilatorBackend.java`.
It covers the design decisions, build pipeline, Java/native process boundary,
state layout, protocol, clock semantics, value conversions, and all major
methods.

## 1. Purpose and architectural decision

The backend lets Doppio use MLIR as a hardware simulation source while preserving
the normal `SimulatorBackend` API:

```text
time()
read(port)
write(port, value)
previous(port)
step()
close()
```

Forcing Arcilator's JIT to become an interactive Java object would require
keeping JIT internals, native symbol lookup, and Arc state all in the Java
process. Instead, this implementation uses Arcilator as a build tool and creates
a small native C++ simulation process. Java communicates with that process using
a line-oriented stdin/stdout protocol.

This is the simplest architecture that provides:

* persistent state across Java `step()` calls;
* input writes and output reads;
* arbitrary-width ports;
* explicit clock stepping;
* child-process cleanup;
* no JNI or shared-library dependency.

The generated native process is a protocol adapter around the emitted model. It
is not an independent copy of the hardware description.

## 2. End-to-end flow

The complete lifecycle is:

```text
new ArcilatorConfig(...)
        |
        v
new ArcilatorBackend(config)
        |
        +--> validate configuration
        |
        +--> arcilator --state-file ... --emit-llvm ...
        |          |
        |          +--> JSON state/offset metadata
        |          +--> lowered LLVM IR
        |
        +--> parse state JSON
        |
        +--> Java generates C++ protocol driver
        |
        +--> clang++ compiles LLVM object + C++ driver
        |
        +--> Java starts the native executable
        |
        +--> Sim and InPort/OutPort exchange Java calls
        |
        +--> Java sends text commands to process stdin
        |
        +--> C++ accesses model state, evaluates model, or steps clock
        |
        +--> C++ writes text response to process stdout
        |
        +--> backend.close()
                    |
                    +--> send finish
                    +--> terminate process if needed
```

Each `Sim.step()` call ultimately causes Java to send `step`; Java does not
directly manipulate the MLIR state. Each output read causes Java to send `read`;
Java does not parse LLVM or execute it itself.

## 3. Configuration

`ArcilatorConfig` contains:

| Field | Meaning |
|---|---|
| `mlirFile` | The MLIR file to lower |
| `buildDir` | Directory containing generated metadata, LLVM, C++, object, and executable |
| `clockPort` | Name of the input state entry used for the sole clock |
| `ports` | Optional input/output subset; an empty list enables discovery |
| `arcilatorCommand` | Arcilator executable name or absolute path |
| `traceEnabled` | Reserved; native protocol tracing is not implemented yet |

When the build directory is omitted, Arcilator configs use
`build/doppio-arcilator/<mlir-file-stem>` and Verilator configs use
`build/doppio-verilator/<top-module>`. Both backends create their build
directory before writing generated files. Existing constructors that accept
an explicit build directory remain available. The convenience constructors
default the clock port to `clock` for Arcilator and `clk` for Verilator; callers
can still supply a different name.

The port records are deliberately similar to `VerilatorConfig`:

```java
ArcilatorConfig config = new ArcilatorConfig(Path.of("src/mlir/Accumulator.mlir"));
```

Or provide a subset to expose only selected ports:

```java
ArcilatorConfig subset = new ArcilatorConfig(
    Path.of("src/mlir/Accumulator.mlir"), List.of(
        ArcilatorConfig.input("reset", 1),
        ArcilatorConfig.output("out", 8)));
```

An empty port list (as in the first constructor) exposes all non-clock input
and output ports discovered from Arcilator's generated state metadata. A
non-empty list remains an optional subset declaration, and its names, direction,
and widths are validated against that metadata.

## 4. Constructor flow

The constructor performs all expensive setup:

1. Rejects a null configuration.
2. Rejects trace mode, which is not implemented for the native path.
3. Calls `buildSimulation()`.
4. Discovers all ports or validates the explicitly selected subset against the
   state JSON.
5. Verifies that the configured clock exists as an input.
6. Starts the native executable.
7. Creates UTF-8 buffered stdin/stdout streams.

This separation is important: all later `read`, `write`, `previous`, and `step`
calls only communicate with an already-running process.

## 5. Build pipeline

### 5.1 MLIR lowering

`buildSimulation()` asks Arcilator to generate port-aware state metadata:

```text
arcilator
  --observe-ports
  --no-runtime
  --state-file=<build/doppio-arcilator-state.json>
  --emit-llvm
  -o <build/doppio-arcilator-model.ll>
  <input.mlir>
```

`--observe-ports` makes input and output port entries observable in the state
description. `--state-file` writes the offsets, widths, and directions.
`--emit-llvm` writes the lowered implementation. `--no-runtime` prevents the
lowered LLVM from depending on the Arc runtime library.

For the Counter design, state JSON contains entries such as:

```json
{
  "name": "Counter",
  "numStateBytes": 34,
  "states": [
    {"name": "clock", "offset": 24, "numBits": 1, "type": "input"},
    {"name": "reset", "offset": 25, "numBits": 1, "type": "input"},
    {"name": "io_enable", "offset": 26, "numBits": 1, "type": "input"},
    {"name": "io_count", "offset": 33, "numBits": 4, "type": "output"}
  ]
}
```

The offsets come from the lowering pipeline. They must not be guessed in Java.
The JSON is an internal state discovery mechanism used to bind names to byte
offsets.

### 5.2 State JSON parsing

`parseStateFile()` reads:

* model name;
* `numStateBytes`;
* optional `initialFnSym`;
* input port name, byte offset, and width;
* output port name, byte offset, and width.

It stores input and output entries in `Map<String, StatePort>`.
The implementation does not introduce a JSON library. It uses regular expressions
only for this constrained, tool-generated JSON structure.

If no output is present, the backend throws an error rather than silently
building a model with no observable values.

### 5.3 C++ harness generation

`harnessSource()` writes C++ source at runtime using Java `StringBuilder`.
It declares and calls a generated function with the model name:

```cpp
extern "C" void Counter_eval(void* state);
```

The state layout is a byte array:

```cpp
uint8_t* state = (uint8_t*)malloc(numStateBytes);
```

The generated C++:

* reads input/output bytes;
* writes input bytes;
* finds the configured clock offset;
* calls the lowered model's `_eval` function;
* hosts the line-oriented command loop.

The C++ harness is an adapter, not a second simulation implementation.

### 5.4 Native compilation

The LLVM lowering is first compiled by `clang++`:

```text
clang++ -std=c++17 -c doppio-arcilator-model.ll
                    -o doppio-arcilator-model.o
```

It is then linked with the generated C++ protocol program:

```text
clang++ -std=c++17 doppio-arcilator-main.cpp
                    doppio-arcilator-model.o
                    -o doppio-arcilator
```

The executable is placed at:

```text
<buildDir>/doppio-arcilator
```

All commands run with merged diagnostics. If compilation fails, Java includes
the external tool output in `IllegalStateException`.

## 6. Java/native protocol

After construction, Java and C++ communicate on process stdin/stdout.

Protocol commands:

```text
read <port>
write <port> <hex-value>
step
finish
```

Protocol responses:

```text
OK
OK <hex-value>
ERR <message>
```

### 6.1 Java command sender

`command()` does four important operations:

1. writes the command to `BufferedWriter`;
2. calls `newLine()` and `flush()`;
3. reads exactly one response line;
4. translates the response into `BigInteger`, success, or an exception.

`OK` returns `BigInteger.ZERO` internally. This is only an implementation
convenient for methods that do not return values.

`OK <hex>` creates:

```java
new BigInteger(hex, 16)
```

`ERR <message>` and unexpected text become `IllegalStateException`.
An EOF from the process becomes an error mentioning the failed command.

There is no background protocol thread. Reads block Java until C++ has completed
the operation and flushed a response.

### 6.2 C++ protocol receiver

The C++ `main()` function:

```cpp
while (std::cin >> command) {
    // handle read, write, step, or finish
}
```

It never writes diagnostics to stdout. Every protocol result is a single line
beginning with `OK` or `ERR`, preventing diagnostic text from corrupting reads.

The native process intentionally remains alive after each command.

## 7. State access and offsets

For a port at offset `O` and width `W`, C++ reads its storage at:

```text
state[O .. O + ceil(W / 8) - 1]
```

State is read least-significant byte first.

### 7.1 Reading

`read_bits(offset, width, hex)`:

1. calculates the number of bytes;
2. walks bytes from most significant to least significant;
3. removes leading zero bytes;
4. adds two hex digits for complete following bytes;
5. masks a partially used final byte;
6. returns `"0"` if no bits are set.

Java then parses the result as a hexadecimal `BigInteger`.

### 7.2 Writing

`write(port, BigInteger)` in Java:

1. confirms the port exists;
2. confirms it is an input;
3. rejects negative values;
4. rejects values not fitting the configured width;
5. converts the value to hexadecimal text;
6. sends `write <name> <hex>`.

The C++ side:

1. validates the hexadecimal characters;
2. pads to the configured byte width;
3. converts pairs of hex digits to bytes;
4. writes those bytes at the state offset;
5. masks partial bytes for non-byte-aligned widths.

The Java width validation is the main user-facing guard. C++ padding is a
second guard at the native boundary.

## 8. Clock and `step()` semantics

The backend assumes one explicitly configured clock port.

For every `step()`:

```cpp
set_clock(0);
Model_eval(state);

set_clock(1);
Model_eval(state);

set_clock(0);
Model_eval(state);
```

This convention:

1. removes the previous rising-edge state;
2. drives the clock high;
3. evaluates the model to capture the rising edge;
4. restores the clock low.

The low/evaluate transitions ensure ports and state settle consistently. The
cycle count stored by Java starts at zero and increments once for each successful
native `step`.

The default primary clock name is `clock`; users can override it with
`ArcilatorConfig.clockPort`. The current implementation does not support
several independent clock domains.

## 9. Generated build artifacts

For the accumulator, the build directory contains:

```text
doppio-arcilator-state.json
doppio-arcilator-model.ll
doppio-arcilator-model.o
doppio-arcilator-main.cpp
doppio-arcilator
```

These files make each stage inspectable:

* JSON explains names, offsets, widths, and directions;
* LLVM explains the lowered model;
* C++ explains protocol behavior;
* object/executable are the final native simulation.

## 10. Limitations

Compared with `VerilatorBackend`, the current Arcilator backend has:

* one clock port instead of a generic clock abstraction;
* no native VCD trace despite a `traceEnabled` configuration field;
* no JIT-hosted shared object or direct library calls;
* no support for independent clock domains;
* an external native compiler requirement (`clang++`);
* startup compilation cost for every newly constructed backend.

It does, however, keep test code independent of the underlying backend:
the same `Sim`, `InPort`, `OutPort`, and `step()` operations are used.

## 11. Design tradeoffs

Arcilator JIT `--run` is batch-oriented and is unsuitable for persistent
interactive `read`/`write` calls. Directly spawning `arcilator --run` after every
Java operation would lose hardware state and make clock semantics ambiguous.

A custom C++ protocol process is therefore preferable to an interactive JIT
shell: lowering is done once, state remains resident, and Java sees a simple
line protocol. Native state offsets are discovered from Arcilator rather than
reimplemented in Java.