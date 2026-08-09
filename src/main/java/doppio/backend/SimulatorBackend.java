package doppio.backend;

public interface SimulatorBackend {
    long time();

    long read(String portName);

    void write(String portName, long value);

    long previous(String portName);

    void step();
}
