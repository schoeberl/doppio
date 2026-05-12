package doppio.backend;

import java.nio.file.Path;
import java.util.List;

public record VerilatorConfig(
        Path topFile,
        String topModule,
        List<Path> includeDirs,
        Path buildDir,
        boolean traceEnabled) {
}
