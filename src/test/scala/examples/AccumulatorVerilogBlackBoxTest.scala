package examples

import chisel3._
import chisel3.util._
import chisel3.simulator._
import doppio.Sim
import doppio.backend.{ChiselSimBackend, ChiselSimSignal}
import doppio.examples.AccumulatorJavaTest
import org.scalatest.funsuite.AnyFunSuite


class AccumulatorBlackBox(val width: Int) extends BlackBox with HasBlackBoxPath {
  override def desiredName = "accumulator"
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val en  = Input(Bool())
    val in  = Input(UInt(width.W))
    val out = Output(UInt(width.W))
  })
  addPath("src/verilog/accumulator.v")
}

class AccumulatorBlackBoxWrapper(val width: Int) extends Module {
  val io = IO(new Bundle {
    val rst = Input(Bool())
    val en  = Input(Bool())
    val in  = Input(UInt(width.W))
    val out = Output(UInt(width.W))
  })
  val blackbox = Module(new AccumulatorBlackBox(width))
  blackbox.io.clk := clock
  blackbox.io.rst := io.rst
  blackbox.io.en  := io.en
  blackbox.io.in  := io.in
  io.out := blackbox.io.out
}

class AccumulatorVerilogBlackBoxTest extends AnyFunSuite with ChiselSim {
  test("Accumulator Verilog black box accumulates input when enabled and resets correctly") {
  simulate(new AccumulatorBlackBoxWrapper(8)) { dut =>
      // Reset
      dut.io.rst.poke(true.B)
      dut.io.en.poke(false.B)
      dut.io.in.poke(0.U)
      dut.clock.step()
      dut.io.rst.poke(false.B)
      dut.io.out.expect(0.U)

      // Accumulate 1, 2, 3
      dut.io.in.poke(1.U); dut.io.en.poke(true.B); dut.clock.step()
      dut.io.out.expect(1.U)
      dut.io.in.poke(2.U); dut.clock.step()
      dut.io.out.expect(3.U)
      dut.io.in.poke(3.U); dut.clock.step()
      dut.io.out.expect(6.U)

      // Disable enable, value should hold
      dut.io.en.poke(false.B); dut.io.in.poke(7.U); dut.clock.step()
      dut.io.out.expect(6.U)

      // Reset again
      dut.io.rst.poke(true.B); dut.clock.step()
      dut.io.out.expect(0.U)
    }
  }

  test("Java cycle model drives the Accumulator Verilog black box through ChiselSim") {
    simulate(new AccumulatorBlackBoxWrapper(8)) { dut =>
      val signals = new java.util.HashMap[String, ChiselSimSignal]()
      signals.put("rst", ChiselSimSignal.bool(dut.io.rst))
      signals.put("en", ChiselSimSignal.bool(dut.io.en))
      signals.put("in", ChiselSimSignal.uint(dut.io.in))
      signals.put("out", ChiselSimSignal.uint(dut.io.out))

      val backend = new ChiselSimBackend(dut.clock, signals)
      new AccumulatorJavaTest().run(new Sim(backend))
    }
  }
}
