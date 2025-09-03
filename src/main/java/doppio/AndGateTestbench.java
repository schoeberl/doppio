package doppio;

public class AndGateTestbench extends Testbench<AndGate> {
    public AndGateTestbench(AndGate dut) {
        super(dut);
    }

    @Override
    public void run() {
        // Test all input combinations for a 1-bit AND gate
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                dut.a.set(a);
                dut.b.set(b);
                dut.eval();
                int expected = a & b;
                if (dut.y.get() != expected) {
                    System.out.printf("Test failed: a=%d, b=%d, y=%d (expected %d)\n", a, b, dut.y.get(), expected);
                } else {
                    System.out.printf("Test passed: a=%d, b=%d, y=%d\n", a, b, dut.y.get());
                }
            }
        }
    }
}
