import doppio.*;

public class RunRegisterTest {
    public static void main(String[] args) {
        Register dut = new Register();
        RegisterTestbench tb = new RegisterTestbench(dut);
        tb.run();
    }
}
