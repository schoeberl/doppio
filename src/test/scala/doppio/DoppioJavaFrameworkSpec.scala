package doppio

import doppio.backend.InMemoryBackend
import doppio.examples.CounterTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class DoppioJavaFrameworkSpec extends AnyFlatSpec with Matchers {
  "The Java cycle framework" should "run the counter example" in {
    val runner = new TestRunner(_ => InMemoryBackend.withClockedCounter())
    val results = runner.run(classOf[CounterTest])

    results.size() shouldBe 1
    val result = results.get(0)
    if (!result.passed()) {
      fail(result.failure())
    }
  }
}
