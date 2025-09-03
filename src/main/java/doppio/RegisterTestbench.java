
package doppio;
import java.math.BigInteger;

public class RegisterTestbench extends Testbench<Register> {
    public RegisterTestbench(Register dut) {
        super(dut);
    }

    @Override
    public void run() {
        // Test sequence for d and rst
        int[] rstSeq = {1, 1, 0, 0, 0, 0, 0, 0};
        int[] dSeq   = {0, 1, 1, 0, 1, 1, 0, 0};
        int[] expectedQ = {0, 0, 1, 0, 1, 1, 0, 0};

        for (int i = 0; i < rstSeq.length; i++) {
            // Set inputs for this cycle
            dut.rst.set(BigInteger.valueOf(rstSeq[i]));
            dut.d.set(BigInteger.valueOf(dSeq[i]));

            // Clock low phase
            dut.clk.set(BigInteger.ZERO);
            dut.eval();

            // Clock high phase (rising edge)
            dut.clk.set(BigInteger.ONE);
            dut.eval();

            // Check output after rising edge
            BigInteger expected = BigInteger.valueOf(expectedQ[i]);
            if (!dut.q.get().equals(expected)) {
                System.out.printf("Cycle %d: FAIL (d=%d, rst=%d, q=%s, expected=%s)\n", i, dSeq[i], rstSeq[i], dut.q.get().toString(), expected.toString());
            } else {
                System.out.printf("Cycle %d: PASS (d=%d, rst=%d, q=%s)\n", i, dSeq[i], rstSeq[i], dut.q.get().toString());
            }
        }
    }
}
