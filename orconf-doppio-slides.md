# Doppio: Open-Source Verification with Java and Scala

<style>
section {
  padding: 42px 56px;
}
pre {
  font-size: 0.72em;
}
section img {
  vertical-align: middle;
}
</style>

Martin Schoeberl
Technical University of Denmark

---

# Hardware Testing Is Software

Modern testbenches are software systems:

- drivers and monitors
- scoreboards
- random generators
- protocol models
- coverage
- CI and regressions

The testbench often lives as long as the DUT

So the software engineering environment matters

---

# Why JVM?

Python and cocotb showed that mainstream languages can help hardware verification

Doppio asks the same question for the JVM

The JVM gives us:

- static types
- mature IDEs and refactoring
- build and CI tooling
- a large library ecosystem
- supports several languages

---

# Java And Scala

Doppio is not Java versus Scala

It is Java and Scala - a double shot espresso

Java:

- stable baseline
- widely known
  - still in 3rd or 4th position in different indices
- good for long-lived infrastructure

Scala:

- nicer syntax
- natural fit for Chisel users
- can sit on the same Java core

---

# The Core Model

Doppio uses a simple two-phase simulation model

```text
observe phase
  read DUT outputs

drive phase
  write DUT inputs

step()
  advance clock
  return to observe
```

This is the whole mental model

---

# Java Example

```java
dut.run(sim -> {
    InPort rst = sim.inPort("rst");
    InPort en = sim.inPort("en");
    InPort in = sim.inPort("in");
    OutPort out = sim.outPort("out");

    rst.set(1);
    en.set(0);
    in.set(0);
    sim.step();

    sim.expect(out.asLong() == 0, "reset clears accumulator");
});
```

Input and output ports are different types

---

# Scala Binding

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

  sim.expect(out.value == 0, "reset clears accumulator")
}
```

Same simulator

More Scala-shaped syntax

---

# Type-Safe DUT Wrappers

Raw port names are useful early

But tests should not be full of strings

```java
final class AccumulatorInterface {
    public final InPort rst;
    public final InPort en;
    public final InPort in;
    public final OutPort out;

    AccumulatorInterface(Sim sim) {
        rst = sim.inPort("rst");
        en = sim.inPort("en");
        in = sim.inPort("in");
        out = sim.outPort("out");
    }
}
```

This can be generated from simple Verilog

---

# Concurrency

Hardware testbenches need concurrent agents

Doppio has `fork()`

```java
dut.run(sim -> {
    reset(sim);

    sim.fork(() -> produce(sim, stats));
    sim.fork(() -> consume(sim, stats));
});
```

Producer and consumer are plain Java threads

---

# Phase Synchronization

Concurrent agents synchronize at phase boundaries

```text
agent A observe ─┐
agent B observe ─┼─> drive phase
agent C observe ─┘

agent A step ────┐
agent B step ────┼─> one clock tick
agent C step ────┘
```

All agents see the same observe/drive/step rhythm

---

# FIFO Stress Example

A simple Verilog FIFO is tested with two agents:

- producer writes unless `full`
- consumer reads unless `empty`
- 1000 unique data items
- order and loss are checked
- average latency is reported

Example result:

```text
PASS TestConcurrentFifo items=1000 cycles=1004
cycles/item=1.004 average_latency=1.000 cycles
```

---

# Current State

Working examples:

- Java accumulator test
- Java accumulator test with typed wrapper
- Scala accumulator test
- TinyALU Java test
- concurrent FIFO Java test

Backends:

- in-memory test backend
- Verilator for simple Verilog
- experimental ChiselSim bridge

Full check: `make test`

---

# What I Want Feedback On

- Is the observe/drive/step model understandable? Useful?
- Would you like to use Java or Scala for hardware verification?
- Should Doppio stay lightweight?
- How much UVM-like structure is useful? On top of Doppio?
- What examples would make this convincing?

The project is very young => feedback can/shall shape it

- Still freedom to change stuff and play with ideas

---

# Project Context

Doppio is part of the ODE4EC-PIV Chips JU project

The plan is to connect it with related open-source hardware tooling

One concrete direction:

- integrate with Surfer
- use Surfer for waveform-centric debugging
- keep Doppio focused on tests, agents, and simulation control

Surfer is part of the same Chips JU project

I am hiring a PhD to work on this topic!

---

# Summary

Doppio is an experiment in JVM-based hardware verification

Java gives a stable baseline

Scala gives Chisel-friendly syntax

The core model is simple:

```text
observe -> drive -> step
```

With `fork()` for concurrent test agents

<img src="slides/doppio-repo-qr.svg" width="180"> https://github.com/schoeberl/doppio
