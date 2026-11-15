package main.reasoning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Transport boundary for future local adapters; stdout is JSON, stderr is retained separately. */
public final class ReasoningProcess {
    private ReasoningProcess() {}

    public static ReasoningReport run(List<String> command, Duration timeout, Path logDirectory)
            throws IOException, InterruptedException {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive timeout required");
        Files.createDirectories(logDirectory);
        // Unique files prevent stale success output from being reused after a failed launch.
        var stdout = Files.createTempFile(logDirectory, "reasoner-", ".json");
        var stderr = Files.createTempFile(logDirectory, "reasoner-", ".stderr");
        Process process;
        try {
            process = new ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        } catch (IOException e) {
            return ReasoningReport.toolError("Cannot start reasoner: " + e.getMessage());
        }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS))
                return ReasoningReport.toolError("Reasoner timeout after " + timeout);
            if (process.exitValue() != 0)
                return ReasoningReport.toolError("Reasoner process exit code " + process.exitValue());
            return ReasoningProtocol.decode(Files.readString(stdout));
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }
}
