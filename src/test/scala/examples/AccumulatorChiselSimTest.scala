package examples

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import chisel3.simulator._
import org.scalatest.funsuite.AnyFunSuite

class AccumulatorChiselSimTest extends AnyFunSuite with ChiselSim {
  test("Accumulator accumulates input when enabled and resets correctly") {
    simulate(new Accumulator(8)) { dut =>
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
}
