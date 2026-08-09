package doppio

import doppio.backend.InMemoryBackend
import doppio.examples.CounterTest
import org.scalatest.flatspec.AnyFlatSpec

final class DoppioJavaFrameworkSpec extends AnyFlatSpec {
  "The Java cycle framework" should "run the counter example" in {
    val dut = new Sim(InMemoryBackend.withClockedCounter())
    new CounterTest().run(dut)
  }
}
