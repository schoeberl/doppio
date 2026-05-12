package doppio.backend;

public interface SimulatorBackend {
    long time();

    long read(String signalPath);

    void write(String signalPath, long value);

    long previous(String signalPath);

    void step();
}
