package doppio

import doppio.backend.InMemoryBackend
import doppio.scaladsl._
import org.scalatest.flatspec.AnyFlatSpec

final class ScalaBindingSpec extends AnyFlatSpec {
  "The Scala binding" should "use field-style reads and writes" in {
    val dut = new Sim(InMemoryBackend.withClockedCounter())

    dut.runScala { sim =>
      val rst = sim.in("rst")
      val count = sim.out("count")

      sim.expect(count.value == 0, "counter starts at zero")
      rst.value = 1
      sim.step()

      sim.expect(count.value == 0, "reset should clear count")
      rst.value = 0
      sim.step()

      sim.step()
      sim.step()

      sim.expect(count.value == 3, "counter should advance after reset")
    }
  }

  it should "support concurrent Scala agents" in {
    val backend = new InMemoryBackend(b => b.write("ticks", b.read("ticks") + 1))
    val dut = new Sim(backend)

    dut.runScala { sim =>
      val ticks = sim.out("ticks")
      val a = sim.in("a")
      val b = sim.in("b")

      sim.fork {
        sim.expect(ticks.value == 0, "fork should observe initial tick")
        a.value = 1
        sim.step()
        sim.expect(ticks.value == 1, "fork should observe one shared step")
      }

      sim.expect(ticks.value == 0, "main should observe initial tick")
      b.value = 2
      sim.step()
      sim.expect(ticks.value == 1, "main should observe one shared step")
    }

    assert(backend.read("a") == 1)
    assert(backend.read("b") == 2)
    assert(backend.read("ticks") == 1)
  }
}
