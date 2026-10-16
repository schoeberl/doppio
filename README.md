# Doppio: Open-Source Verification with Java and Scala

This repository investigates how to test and verify digital circuits in Java, a modern, statically typed language.
Furthermore, we also want Scala bindings.
It shall replace the cocotb/PyUVM simulation environment.

The goal is a cocotb-like programming model in Java, with ChiselSim and Verilator as backend simulation machinery.

The core simulation model is deliberately simple and has two phases:

1. observe phase: read values from the DUT
2. drive phase: set DUT input ports
3. `step()`: advance the clock by one tick and return to observe phase

This keeps Java tests plain and predictable, without coroutine magic.

## Example

```java
public final class CounterTest {
  public void run(Sim dut) {
    dut.run(sim -> {
      InPort rst = sim.inPort("rst");
      OutPort count = sim.outPort("count");

      sim.expect(count.asLong() == 0, "counter starts at zero");
      rst.set(1);
      sim.step();

      sim.expect(count.asLong() == 0, "reset should clear count");
      rst.set(0);
      sim.step();

      for (int i = 1; i < 4; i++) {
        sim.step();
      }

      sim.expect(count.asLong() == 4, "counter should advance");
    });
  }
}
```

`OutPort.asLong()` reads the current value, `InPort.set(...)` writes an input value, and `sim.step()` advances the clock by one tick. Calling `set(...)` implicitly enters the drive phase; reading an output during the drive phase is illegal until `sim.step()` returns the simulation to observe phase. Use `sim.drive()` when you want to make the phase change explicit before a group of writes. Use `sim.fork(...)` inside `sim.run(...)` to start concurrent Java test agents; all active agents synchronize when the simulation switches from observe to drive and again when `step()` advances the clock.
Create a backend, wrap it in `Sim`, and call the test directly:

```java
Sim dut = new Sim(InMemoryBackend.withClockedCounter());
new CounterTest().run(dut);
```

Run the sample suite:

```sh
sbt "runMain doppio.examples.RunExamples"
```

Run the Verilog accumulator through Verilator from plain Java:

```sh
sbt "runMain doppio.examples.TestAccumulator"
```

Run the same accumulator test with a typed Java helper class around the DUT interface:

```sh
sbt "runMain doppio.examples.TestAccumulatorWithInterface"
```

Run the accumulator test through the Scala binding:

```sh
sbt "runMain doppio.examples.TestAccumulatorScalaBinding"
```

Run the concurrent FIFO example, with separate Java producer and consumer agents:

```sh
sbt "runMain doppio.examples.TestConcurrentFifo"
```

Run the framework self-checks:

```sh
sbt test
```

Extract ports from a simple one-module Verilog/SystemVerilog file:

```sh
make ports
make fifo-ports-java
make accumulator-wrapper-java
make tinyalu-wrapper-java
scripts/extract_verilog_ports.py src/verilog/simple_fifo.v
scripts/extract_verilog_ports.py src/verilog/simple_fifo.v --format java-config --exclude clk
scripts/extract_verilog_ports.py src/verilog/accumulator.v --format java-wrapper --exclude clk --class-name AccumulatorInterface
```

The extractor is intentionally lightweight. It handles simple ANSI-style module headers and can emit `VerilatorConfig.input(...)` / `VerilatorConfig.output(...)` entries or a typed Java wrapper class with `InPort` and `OutPort` fields, but it is not a full Verilog parser.

## Backend Direction

The public Java API talks to `SimulatorBackend`. A Verilator/ChiselSim implementation should provide:

- port lookup and value access
- input writes
- one-cycle advancement in `step()`
- optional VCD/FST tracing controls

That boundary keeps Java tests independent of whether the underlying simulation is a ChiselSim-hosted Verilator process, a JNI bridge, a socket protocol, or a generated JVM wrapper.


## Steps

 * [ ] Start as simple as possible: peek/poke with `BigInt` in Java
   - Just three functions are needed: `poke()`, `peek()`, and `step()`
 * [ ] Maybe start with a dummy backend, e.g., some Java class representing HW
 * [ ] Use the simplest backend possible
 * [ ] Explore Scala features
 * [ ] Read on SW testing

## To Explore

 * Checkout Scala based drivers for Verilator
   - New driver in the Chisel library
   - Simulator from SpineHDL
   - Old driver from ChiselTest
 * What shall the API be?
 * Concurrency: Java Ruannble, ChiselTest fork, some coroutine syntax?
   - For performance reasons, we would like real coroutines (not simulating them with threads)
   - Singel threaded tests shall not have any overhead (SpinalHDL has a notion of callback)

## Questions

 * Shall we start with Scala 3?
 * Maybe a fake HDL for getting started quickly
 * Can we use functional programming to make testing nicer? E.g. as in the follwing ScalaTest example:
```Scala
val xs = 1 to 3
val it = xs.iterator
eventually { it.next() shouldBe 3 }
```

## TODO

 * Explore cocotb and PyUVM
 * UVM in Java? It is industry standard, but do we like it?
 * Find a better abstraction than UVM
 * Can we learn from SW testing?
 * What else is out in the field (related work)?
 * We shall have a ChiselTest compatible API for the transition from Chisel 5/6
 * A better name, I don't like jUvm

## Issues

 * We cannot mix Scala 3 and Chisel (Scala 2)
   - It is possible according to Scala 3 documentation, but macros/compiler plugin might give us troubles
 * We can use Chisel types, but we cannot compute with them, or assign new values

## Annoying Stuff in ChiselTest

 * I want to test different implementations with the same test. Following does not work:
```Scala
  val rfs = List(Module(new RegisterFile()), Module(new RegisterFile2()))

for (rf <- rfs) {
   "RegisterFile" should "pass" in {
      test(rf) {
         d => {
```

## Java Cycle Framework

The `doppio` Java package contains the first plain-Java verification framework slice:

- `Sim.run(...)` for one complete multi-cycle simulation script
- `Sim.fork(...)` for concurrent Java test agents inside a simulation script
- `Sim.drive(...)` to enter the drive phase explicitly
- `Sim.step()` to advance the clock by one tick
- `InPort` handles with `set(...)` for DUT inputs
- `OutPort` handles with `asLong()` and `isHigh()` for DUT outputs
- `SimulatorBackend` as the boundary for ChiselSim/Verilator integrations
- `InMemoryBackend` for deterministic examples and framework tests
- `VerilatorBackend` for running simple Verilog modules from Java

Each step follows the same order: observe DUT ports, drive DUT inputs, then advance the clock by one tick. With forked agents, `drive()` and `step()` are barriers across all active agents, so monitors and drivers see the same phase transitions. A passive agent can call `step()` directly after observing; that marks it as having no drives for the current cycle.

Fork new agents during the observe phase. During `run`, port access, `drive`, `step`, and `fork` must come from the run body or a forked agent; unrelated Java threads are rejected. Agents may finish at different cycles, and nested forks are supported. An uncaught agent or backend failure releases the simulation barriers, interrupts the remaining forked agents, joins them, and rethrows the first failure. If the run owner is interrupted, Doppio interrupts and joins its children before returning, preserving the owner's interrupt status. Agent code that blocks outside Doppio must respond to interruption; code that ignores it can still prevent shutdown. The run body is not interrupted by child failures, so it should use Doppio's barriers rather than wait indefinitely on external events.

Run the Java example directly with:

```sh
sbt "runMain doppio.examples.RunExamples"
```

The framework is intentionally not a test runner. Use plain `main` methods, JUnit, ScalaTest, or any other test framework to create a backend, construct a `Sim`, and call your simulation code. The Java framework smoke test is also wired into the existing ScalaTest suite, so `sbt test` covers it alongside the ChiselSim examples.

## Scala Binding

The `doppio.scaladsl` package adds a thin Scala layer on top of the Java API:

```scala
import doppio.scaladsl._

dut.runScala { sim =>
  val rst = sim.in("rst")
  val en = sim.in("en")
  val in = sim.in("in")
  val out = sim.out("out")

  rst.value = 1
  en.value = 0
  in.value = 0
  sim.step()

  assert(out.value == 0)
}
```

Input writes use Scala setter syntax (`rst.value = 1`), and output reads use field-like access (`out.value`). Input reads still fail at runtime; the input getter exists only because Scala requires it for setter syntax. The same `step`, `drive`, `fork`, and `expect` simulation operations are forwarded to the Java `Sim`.

## Wide Ports

Use Java `BigInteger` or Scala `BigInt` for ports wider than 64 bits. The in-memory, Verilator, and ChiselSim backends preserve wide values, including snapshots returned by `previousBigInteger`.

```java
BigInteger value = BigInteger.ONE.shiftLeft(128).add(BigInteger.valueOf(42));
sim.inPort("in").set(value);
sim.step();
sim.expect(sim.outPort("out").asBigInteger().equals(value), "all 129 bits match");
```

```scala
sim.in("in").bigValue = (BigInt(1) << 128) + 42
sim.step()
assert(sim.out("out").bigValue == (BigInt(1) << 128) + 42)
// Assignment also supports: sim.in("in") := BigInt(...)
```

Values are unsigned. Negative `BigInteger`/`BigInt` writes are rejected. The `long` API retains its 64-bit bit-pattern representation: an unsigned 64-bit value with its top bit set reads as a negative `long`. Reads of values requiring more than 64 bits throw `ArithmeticException`; use the wide accessor to avoid truncation. `isHigh()` checks the entire value.

For Verilator, supply the actual HDL width in the configuration:

```java
VerilatorConfig.input("in", 129),
VerilatorConfig.output("out", 129)
```

The original name-only configuration remains available for ports up to 64 bits. Explicit widths also enforce the unsigned input range. The port extractor emits widths for literal ranges; parameter expressions are annotated for you to supply their elaborated width. Custom backends implementing the original `long` interface continue to work through default wide-access methods limited to 64 bits.

## Waveforms

Set `traceEnabled` to `true` in `VerilatorConfig` to write `waveform.vcd` under the build directory. `config.traceFile()` returns its absolute path. Close the backend with try-with-resources to finalize the file. Each traced run replaces the file at that path; use separate build directories to retain multiple runs.

Trace samples capture the settled inputs before the rising edge, the rising edge, and the falling edge. Each Doppio cycle occupies three waveform time units; `Sim.time()` still counts cycles. Writes within one drive phase settle into the next sample. Closing the backend captures any final writes. Tracing is disabled by default.

Run the 129-bit register example and open its waveform in Surfer:

```sh
make wide
surfer build/doppio-verilator/wide_register/waveform.vcd
```

This produces a standard VCD file using [Verilator's C++ tracing API](https://verilator.org/guide/latest/faq.html#how-do-i-generate-waveforms-traces-in-c). Surfer integration currently consists of opening that file; live viewer control and FST output are not implemented.

## ORConf Slides

Generate the local PDF slide deck with GNU Make:

```sh
gmake pdf
```

## Acknowledgments

This project has been supported by the ODE4EC-PIV project. The ODE4EC-PIV project has received funding from the Chips Joint Undertaking (Chips JU) under Grant Agreement No 101252356, with support from the European Union's Horizon Europe research and innovation programme and the National Funding Authorities of Portugal, Germany, Switzerland, Italy, Austria, Sweden, Spain, and Denmark.