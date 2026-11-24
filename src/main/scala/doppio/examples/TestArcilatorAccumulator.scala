package doppio.examples

import doppio.Sim
import doppio.backend.arcilator.{ArcilatorBackend, ArcilatorConfig}
import doppio.scaladsl._

import java.nio.file.Path

object TestArcilatorAccumulatorScalaBinding {
  def main(args: Array[String]): Unit = {
    val config = new ArcilatorConfig(Path.of("src/mlir/Accumulator.mlir"))

    val backend = new ArcilatorBackend(config)
    try {
      testAccumulator(new Sim(backend))
      println("PASS TestArcilatorAccumulator")
    } finally {
      backend.close()
    }
  }

  private def testAccumulator(dut: Sim): Unit = {
    dut.runScala { sim =>
      val reset = sim.in("reset")
      val en = sim.in("en")
      val in = sim.in("in")
      val out = sim.out("out")

      reset.value = 1
      en.value = 0
      in.value = 0
      sim.step()
      sim.expect(out.value == 0, "reset should clear accumulator")

      reset.value = 0
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

      reset.value = 1
      sim.step()
      sim.expect(out.value == 0, "reset should clear accumulator again")
    }
  }
}
