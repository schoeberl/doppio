package examples

import chisel3._
import chisel3.simulator._

import java.util.function.Consumer

object JavaChiselSimBridge extends ChiselSim {
  def runAccumulator(width: Int, body: Consumer[AccumulatorDriver]): Unit = {
    simulate(new Accumulator(width)) { dut =>
      body.accept(new AccumulatorDriver(dut))
    }
  }
}

final class AccumulatorDriver private[examples] (dut: Accumulator) {
  def reset(value: Boolean): Unit =
    JavaChiselSimBridge.toTestableBool(dut.io.rst).poke(value)

  def enable(value: Boolean): Unit =
    JavaChiselSimBridge.toTestableBool(dut.io.en).poke(value)

  def input(value: Int): Unit =
    JavaChiselSimBridge.toTestableUInt(dut.io.in).poke(BigInt(value))

  def step(): Unit =
    JavaChiselSimBridge.toTestableClock(dut.clock).step()

  def step(cycles: Int): Unit =
    JavaChiselSimBridge.toTestableClock(dut.clock).step(cycles)

  def expectOutput(value: Int): Unit =
    JavaChiselSimBridge.toTestableUInt(dut.io.out).expect(BigInt(value))

  def output(): BigInt =
    JavaChiselSimBridge.toTestableUInt(dut.io.out).peekValue().asBigInt
}
