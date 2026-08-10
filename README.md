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
      Port rst = sim.port("rst");
      Port count = sim.port("count");

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

`Port.asLong()` reads the current value, `Port.set(...)` writes an input value, and `sim.step()` advances the clock by one tick. Calling `set(...)` implicitly enters the drive phase; reading a port during the drive phase is illegal until `sim.step()` returns the simulation to observe phase. Use `sim.drive()` when you want to make the phase change explicit before a group of writes. Use `sim.fork(...)` inside `sim.run(...)` to start concurrent Java test agents; all active agents synchronize when the simulation switches from observe to drive and again when `step()` advances the clock.
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

Run the concurrent FIFO example, with separate Java producer and consumer agents:

```sh
sbt "runMain doppio.examples.TestConcurrentFifo"
```

Run the framework self-checks:

```sh
sbt test
```

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
- `Port` handles with `asLong()`, `isHigh()`, and `set(...)`
- `SimulatorBackend` as the boundary for ChiselSim/Verilator integrations
- `InMemoryBackend` for deterministic examples and framework tests
- `VerilatorBackend` for running simple Verilog modules from Java

Each step follows the same order: observe DUT ports, drive DUT inputs, then advance the clock by one tick. With forked agents, `drive()` and `step()` are barriers across all active agents, so monitors and drivers see the same phase transitions. A passive agent can call `step()` directly after observing; that marks it as having no drives for the current cycle.

Run the Java example directly with:

```sh
sbt "runMain doppio.examples.RunExamples"
```

The framework is intentionally not a test runner. Use plain `main` methods, JUnit, ScalaTest, or any other test framework to create a backend, construct a `Sim`, and call your simulation code. The Java framework smoke test is also wired into the existing ScalaTest suite, so `sbt test` covers it alongside the ChiselSim examples.
