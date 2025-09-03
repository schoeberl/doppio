
package doppio;
import java.math.BigInteger;

public class AndGateTestbench extends Testbench<AndGate> {
    public AndGateTestbench(AndGate dut) {
        super(dut);
    }

    @Override
    public void run() {
        // Test all input combinations for a 1-bit AND gate
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                dut.a.set(BigInteger.valueOf(a));
                dut.b.set(BigInteger.valueOf(b));
                dut.eval();
                BigInteger expected = BigInteger.valueOf(a & b);
                if (!dut.y.get().equals(expected)) {
                    System.out.printf("Test failed: a=%d, b=%d, y=%s (expected %s)\n", a, b, dut.y.get().toString(), expected.toString());
                } else {
                    System.out.printf("Test passed: a=%d, b=%d, y=%s\n", a, b, dut.y.get().toString());
                }
            }
        }
    }
}
