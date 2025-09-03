package doppio;

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
            dut.rst.set(rstSeq[i]);
            dut.d.set(dSeq[i]);

            // Clock low phase
            dut.clk.set(0);
            dut.eval();

            // Clock high phase (rising edge)
            dut.clk.set(1);
            dut.eval();

            // Check output after rising edge
            if (dut.q.get() != expectedQ[i]) {
                System.out.printf("Cycle %d: FAIL (d=%d, rst=%d, q=%d, expected=%d)\n", i, dSeq[i], rstSeq[i], dut.q.get(), expectedQ[i]);
            } else {
                System.out.printf("Cycle %d: PASS (d=%d, rst=%d, q=%d)\n", i, dSeq[i], rstSeq[i], dut.q.get());
            }
        }
    }
}
