package doppio.examples

import doppio.Sim
import doppio.backend.{VerilatorBackend, VerilatorConfig}
import doppio.scaladsl._

import java.nio.file.Path
import java.util.List

object TestAccumulatorScalaBinding {
  def main(args: Array[String]): Unit = {
    val config = new VerilatorConfig(
      Path.of("src/verilog/accumulator.v"),
      "accumulator",
      List.of(Path.of("src/verilog")),
      Path.of("build/doppio-verilator/accumulator-scala-binding"),
      false,
      "clk",
      List.of(
        VerilatorConfig.input("rst"),
        VerilatorConfig.input("en"),
        VerilatorConfig.input("in"),
        VerilatorConfig.output("out")
      )
    )

    val backend = new VerilatorBackend(config)
    try {
      testAccumulator(new Sim(backend))
      println("PASS TestAccumulatorScalaBinding")
    } finally {
      backend.close()
    }
  }

  private def testAccumulator(dut: Sim): Unit = {
    dut.runScala { sim =>
      val rst = sim.in("rst")
      val en = sim.in("en")
      val in = sim.in("in")
      val out = sim.out("out")

      rst.value = 1
      en.value = 0
      in.value = 0
      sim.step()

      sim.expect(out.value == 0, "reset should clear accumulator")
      rst.value = 0
      sim.step()

      en.value = 1
      in.value = 1
      sim.step()
      sim.expect(out.value == 1, "accumulator should include first input")

      in.value = 2
      sim.step()
      sim.expect(out.value == 3, "accumulator should include second input")

      in.value = 3
      sim.step()
      sim.expect(out.value == 6, "accumulator should include third input")

      en.value = 0
      in.value = 7
      sim.step()
      sim.expect(out.value == 6, "disabled accumulator should hold its value")

      rst.value = 1
      sim.step()
      sim.expect(out.value == 0, "reset should clear accumulator again")
    }
  }
}
