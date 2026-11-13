package main;

import main.config.ConfigLoader;
import java.nio.file.Path;

/** Small CLI; ontology paths are resolved relative to the configuration file. */
public class Application {
    public static void main(String... args) throws Exception {
        Path config = Path.of("scripts/java/application.yaml");
        String version = System.getProperty("soma.version");
        String commit = System.getProperty("soma.commit");
        for (String arg : args) {
            if (arg.equals("--help")) {
                System.out.println("Usage (repository root): java -jar scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar [--versionInfo=VERSION] [--config=scripts/java/application.yaml] [--commit=SHA]");
                return;
            } else if (arg.startsWith("--versionInfo=")) version = arg.substring(14);
            else if (arg.startsWith("--config=")) config = Path.of(arg.substring(9));
            else if (arg.startsWith("--commit=")) commit = arg.substring(9);
            else throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        var settings = ConfigLoader.load(config);
        CIRunner.run(settings, version, commit == null ? sourceRevision(settings.directory()) : commit);
    }

    private static String sourceRevision(Path directory) throws Exception {
        var root = directory.toRealPath().getParent();
        try {
            var process = new ProcessBuilder("git", "-c", "safe.directory=" + root, "-C", root.toString(), "rev-parse", "HEAD")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); return "unknown"; }
            var revision = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 ? revision : "unknown";
        } catch (java.io.IOException unavailable) { return "unknown"; }
    }
}
