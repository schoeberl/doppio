package doppio.examples;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import doppio.InPort;
import doppio.OutPort;
import doppio.Sim;
import doppio.backend.VerilatorBackend;
import doppio.backend.VerilatorConfig;

public final class TestTinyAlu {
    private TestTinyAlu() {
    }

    public static void main(String[] args) {
        VerilatorConfig config = new VerilatorConfig(
                Path.of("src/verilog/tinyalu.sv"),
                "tinyalu",
                List.of(Path.of("src/verilog")),
                Path.of("build/doppio-verilator/tinyalu"),
                false,
                "clk",
                List.of(
                        VerilatorConfig.input("A"),
                        VerilatorConfig.input("B"),
                        VerilatorConfig.input("op"),
                        VerilatorConfig.input("reset_n"),
                        VerilatorConfig.input("start"),
                        VerilatorConfig.output("done"),
                        VerilatorConfig.output("result")));

        try (VerilatorBackend backend = new VerilatorBackend(config)) {
            testTinyAlu(new Sim(backend));
            System.out.println("PASS TestTinyAlu");
        }
    }

    private static void testTinyAlu(Sim dut) {
        dut.run(sim -> {
            TinyAluBfm bfm = new TinyAluBfm(sim);
            Set<Op> covered = EnumSet.noneOf(Op.class);

            bfm.reset();
            runRandomPerOp(sim, bfm, covered);
            runMaxPerOp(sim, bfm, covered);
            checkCoverage(sim, covered);
            runFibonacci(sim, bfm);
        });
    }

    private static void runRandomPerOp(Sim sim, TinyAluBfm bfm, Set<Op> covered) {
        Random random = new Random(0x51A7EEDL);
        for (Op op : Op.values()) {
            int a = random.nextInt(256);
            int b = random.nextInt(256);
            checkOperation(sim, bfm, covered, a, b, op);
        }
    }

    private static void runMaxPerOp(Sim sim, TinyAluBfm bfm, Set<Op> covered) {
        for (Op op : Op.values()) {
            checkOperation(sim, bfm, covered, 0xff, 0xff, op);
        }
    }

    private static void runFibonacci(Sim sim, TinyAluBfm bfm) {
        int previous = 0;
        int current = 1;
        int[] expected = {0, 1, 1, 2, 3, 5, 8, 13, 21};

        sim.expect(previous == expected[0], "first Fibonacci value");
        sim.expect(current == expected[1], "second Fibonacci value");
        for (int i = 2; i < expected.length; i++) {
            int next = bfm.sendOp(previous, current, Op.ADD);
            sim.expect(next == expected[i], "unexpected Fibonacci value at index " + i);
            previous = current;
            current = next;
        }
    }

    private static void checkOperation(Sim sim, TinyAluBfm bfm, Set<Op> covered, int a, int b, Op op) {
        int actual = bfm.sendOp(a, b, op);
        int expected = predict(a, b, op);
        sim.expect(actual == expected, String.format(
                "0x%02x %s 0x%02x produced 0x%04x, expected 0x%04x",
                a, op, b, actual, expected));
        covered.add(op);
    }

    private static void checkCoverage(Sim sim, Set<Op> covered) {
        for (Op op : Op.values()) {
            sim.expect(covered.contains(op), "missing TinyALU operation coverage for " + op);
        }
    }

    private static int predict(int a, int b, Op op) {
        int aa = a & 0xff;
        int bb = b & 0xff;
        switch (op) {
            case ADD:
                return (aa + bb) & 0xffff;
            case AND:
                return (aa & bb) & 0xffff;
            case XOR:
                return (aa ^ bb) & 0xffff;
            case MUL:
                return (aa * bb) & 0xffff;
            default:
                throw new IllegalArgumentException("unknown operation: " + op);
        }
    }

    private enum Op {
        ADD(1),
        AND(2),
        XOR(3),
        MUL(4);

        private final int code;

        Op(int code) {
            this.code = code;
        }
    }

    private static final class TinyAluBfm {
        private final Sim sim;
        private final InPort a;
        private final InPort b;
        private final InPort op;
        private final InPort resetN;
        private final InPort start;
        private final OutPort done;
        private final OutPort result;

        private TinyAluBfm(Sim sim) {
            this.sim = sim;
            a = sim.inPort("A");
            b = sim.inPort("B");
            op = sim.inPort("op");
            resetN = sim.inPort("reset_n");
            start = sim.inPort("start");
            done = sim.outPort("done");
            result = sim.outPort("result");
        }

        private void reset() {
            a.set(0);
            b.set(0);
            op.set(0);
            start.set(0);
            resetN.set(0);
            sim.step();
            sim.step();
            resetN.set(1);
            sim.step();
        }

        private int sendOp(int left, int right, Op operation) {
            a.set(left & 0xff);
            b.set(right & 0xff);
            op.set(operation.code);
            start.set(1);

            int timeout = operation == Op.MUL ? 8 : 2;
            for (int i = 0; i < timeout; i++) {
                sim.step();
                if (done.isHigh()) {
                    int value = (int) result.asLong() & 0xffff;
                    start.set(0);
                    sim.step();
                    return value;
                }
            }
            throw new AssertionError("TinyALU operation timed out: " + operation);
        }
    }
}
