package main;

import main.config.ConfigLoader;
import java.nio.file.Path;

/** Small CLI; configuration paths retain their historical working-directory semantics. */
public class Application {
    public static void main(String... args) throws Exception {
        Path config = Path.of("application.yaml");
        String version = System.getProperty("soma.version");
        for (String arg : args) {
            if (arg.equals("--help")) {
                System.out.println("Usage: java -jar IsDefinedInAdder-1.0-SNAPSHOT.jar --versionInfo=VERSION [--config=application.yaml]");
                return;
            } else if (arg.startsWith("--versionInfo=")) version = arg.substring(14);
            else if (arg.startsWith("--config=")) config = Path.of(arg.substring(9));
            else throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        if (version == null || version.isBlank()) throw new IllegalArgumentException("--versionInfo or -Dsoma.version is required");
        CIRunner.run(ConfigLoader.load(config), version);
    }
}
