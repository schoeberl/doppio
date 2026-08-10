package doppio

import doppio.backend.InMemoryBackend
import doppio.examples.CounterTest
import org.scalatest.flatspec.AnyFlatSpec

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
}
