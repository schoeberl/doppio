package doppio

import doppio.backend.InMemoryBackend
import doppio.examples.CounterTest
import org.scalatest.flatspec.AnyFlatSpec

import java.util.concurrent.ConcurrentLinkedQueue

final class DoppioJavaFrameworkSpec extends AnyFlatSpec {
  "The Java cycle framework" should "run the counter example" in {
    val dut = new Sim(InMemoryBackend.withClockedCounter())
    new CounterTest().run(dut)
  }

  it should "enter drive phase explicitly and return to observe on step" in {
    val backend = new InMemoryBackend(_ => ())
    val dut = new Sim(backend)
    val value = dut.port("value")

    dut.drive()
    value.set(42)
    assertThrows[IllegalStateException] {
      value.asLong()
    }

    dut.step()
    assert(value.asLong() == 42)
  }

  it should "enter drive phase implicitly on set" in {
    val backend = new InMemoryBackend(_ => ())
    val dut = new Sim(backend)
    val value = dut.port("value")

    value.set(7)
    assertThrows[IllegalStateException] {
      value.asLong()
    }

    dut.step()
    assert(value.asLong() == 7)
  }

  it should "allow grouped writes with drive" in {
    val backend = new InMemoryBackend(_ => ())
    val dut = new Sim(backend)
    val a = dut.port("a")
    val b = dut.port("b")

    dut.drive(() => {
      a.set(1)
      b.set(2)
    })

    dut.step()
    assert(a.asLong() == 1)
    assert(b.asLong() == 2)
  }

  it should "synchronize forked agents at drive and step" in {
    val backend = new InMemoryBackend(b => b.write("ticks", b.read("ticks") + 1))
    val dut = new Sim(backend)
    val events = new ConcurrentLinkedQueue[String]()

    dut.run(sim => {
      val ticks = sim.port("ticks")
      val a = sim.port("a")
      val b = sim.port("b")

      sim.fork(() => {
        events.add("fork observed " + ticks.asLong())
        a.set(1)
        assertThrows[IllegalStateException] {
          ticks.asLong()
        }
        sim.step()
        events.add("fork after " + ticks.asLong())
      })

      events.add("main observed " + ticks.asLong())
      b.set(2)
      assertThrows[IllegalStateException] {
        ticks.asLong()
      }
      sim.step()
      events.add("main after " + ticks.asLong())
    })

    assert(backend.read("a") == 1)
    assert(backend.read("b") == 2)
    assert(backend.read("ticks") == 1)
    assert(events.contains("main observed 0"))
    assert(events.contains("fork observed 0"))
    assert(events.contains("main after 1"))
    assert(events.contains("fork after 1"))
  }

  it should "let passive forked agents advance through the shared phase barriers" in {
    val backend = new InMemoryBackend(b => b.write("ticks", b.read("ticks") + 1))
    val dut = new Sim(backend)
    val events = new ConcurrentLinkedQueue[String]()

    dut.run(sim => {
      val ticks = sim.port("ticks")
      val value = sim.port("value")

      sim.fork(() => {
        events.add("monitor observed " + ticks.asLong())
        sim.step()
        events.add("monitor after " + ticks.asLong())
      })

      value.set(9)
      sim.step()
    })

    assert(backend.read("value") == 9)
    assert(backend.read("ticks") == 1)
    assert(events.contains("monitor observed 0"))
    assert(events.contains("monitor after 1"))
  }

  it should "propagate failures from forked agents" in {
    val backend = new InMemoryBackend(_ => ())
    val dut = new Sim(backend)

    val error = intercept[AssertionError] {
      dut.run(sim => {
        sim.fork(() => {
          throw new AssertionError("forked agent failed")
        })
      })
    }

    assert(error.getMessage == "forked agent failed")
  }
}
