package examples;

public final class RunAccumulatorChiselSim {
    private RunAccumulatorChiselSim() {
    }

    public static void main(String[] args) {
        JavaChiselSimBridge.runAccumulator(8, dut -> {
            dut.reset(true);
            dut.enable(false);
            dut.input(0);
            dut.step();
            dut.reset(false);
            dut.expectOutput(0);

            dut.input(1);
            dut.enable(true);
            dut.step();
            dut.expectOutput(1);

            dut.input(2);
            dut.step();
            dut.expectOutput(3);

            dut.input(3);
            dut.step();
            dut.expectOutput(6);

            dut.enable(false);
            dut.input(7);
            dut.step();
            dut.expectOutput(6);

            dut.reset(true);
            dut.step();
            dut.expectOutput(0);
        });
    }
}
