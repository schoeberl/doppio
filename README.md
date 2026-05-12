# Doppio: Open-Source Verification with Java and Scala

This repository investigates how to test and verify digital circuits in Java, a modern, statically typed language.
Furthermore, we also want Scala bindings.
It shall replace the cocotb/PyUVM simulation environment.

The goal is a cocotb-like programming model in Java, with ChiselSim and Verilator as backend simulation machinery.

The core simulation model is deliberately simple. Each cycle does exactly this:

1. read values from the DUT
2. switch to a writing phase
3. write DUT input ports
4. advance the clock by one tick

This keeps Java tests plain and predictable, without coroutine magic.

## Example

```java
public final class CounterTest {
  @HardwareTest
  public void counts(Sim dut) {
    Signal rst = dut.signal("rst");
    Signal count = dut.signal("count");

    dut.cycle(cycle -> {
      dut.expect(count.asLong() == 0, "counter starts at zero");
      cycle.write(() -> rst.set(1));
    });

    dut.cycle(cycle -> {
      dut.expect(count.asLong() == 0, "reset should clear count");
      cycle.write(() -> rst.set(0));
    });

    for (int i = 1; i < 4; i++) {
      dut.cycle(cycle -> {
      });
    }

    dut.expect(count.asLong() == 4, "counter should advance");
  }
}
```

`Signal.set(...)` is only legal inside `cycle.write(...)`; reads are available during the read phase and after completed cycles.
After each completed cycle, signals also expose their previous sampled value:

```java
dut.cycle();

dut.expect(count.asLong() == 1, "counter advances");
dut.expect(count.previousAsLong() == 0, "last cycle is still visible");
dut.expect(count.rose(), "count moved from zero to non-zero");
```

Use `dut.cycle()` for an empty cycle and `dut.cycles(n)` to advance multiple empty cycles.

Run the sample suite:

```sh
sbt "runMain doppio.examples.RunExamples"
```

Run the framework self-checks:

```sh
sbt test
```

## Backend Direction

The public Java API talks to `SimulatorBackend`. A Verilator/ChiselSim implementation should provide:

- signal lookup and value access
- input writes during the framework write phase
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

- `@HardwareTest` discovery
- `Sim.cycle(...)` as the read/write/tick primitive
- `Signal` handles with read-phase checks and write-phase enforcement
- `SimulatorBackend` as the boundary for ChiselSim/Verilator integrations
- `InMemoryBackend` for deterministic examples and framework tests

Each cycle follows the same four steps: read values from the DUT, switch to the write phase, write DUT inputs, then advance the clock by one tick.

Run the Java example directly with:

```sh
sbt "runMain doppio.examples.RunExamples"
```

The Java framework smoke test is also wired into the existing ScalaTest suite, so `sbt test` covers it alongside the ChiselSim examples.
