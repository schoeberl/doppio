package doppio

import org.scalatest.funsuite.AnyFunSuite
import java.math.BigInteger

class AndGateSpec extends AnyFunSuite {
  test("AndGate produces correct output for all input combinations") {
    val dut = new AndGate()
    for (a <- 0 to 1; b <- 0 to 1) {
      dut.a.setFromTestbench(BigInteger.valueOf(a))
      dut.b.setFromTestbench(BigInteger.valueOf(b))
      dut.eval()
      val expected = BigInteger.valueOf(a & b)
      assert(dut.y.getFromTestbench() == expected, s"a=$a, b=$b, y=${dut.y.getFromTestbench()}, expected=$expected")
    }
  }
}
