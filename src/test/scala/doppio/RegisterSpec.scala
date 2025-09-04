package doppio

import org.scalatest.funsuite.AnyFunSuite
import java.math.BigInteger

class RegisterSpec extends AnyFunSuite {
  test("Register stores and resets correctly on clock edge") {
    val dut = new Register()
    val rstSeq = Array(1, 1, 0, 0, 0, 0, 0, 0)
    val dSeq   = Array(0, 1, 1, 0, 1, 1, 0, 0)
    val expectedQ = Array(0, 0, 1, 0, 1, 1, 0, 0)
    for (i <- rstSeq.indices) {
      dut.rst.setFromTestbench(BigInteger.valueOf(rstSeq(i)))
      dut.d.setFromTestbench(BigInteger.valueOf(dSeq(i)))
      dut.clk.setFromTestbench(BigInteger.ZERO)
      dut.eval()
      dut.clk.setFromTestbench(BigInteger.ONE)
      dut.eval()
      val expected = BigInteger.valueOf(expectedQ(i))
      assert(dut.q.getFromTestbench() == expected, s"Cycle $i: d=${dSeq(i)}, rst=${rstSeq(i)}, q=${dut.q.getFromTestbench()}, expected=$expected")
    }
  }
}
