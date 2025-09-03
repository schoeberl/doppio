import doppio.*;

public class RunAndGateTest {
    public static void main(String[] args) {
        AndGate dut = new AndGate();
        AndGateTestbench tb = new AndGateTestbench(dut);
        tb.run();
    }
}
