package main;

import main.config.ConfigLoader;
import java.nio.file.Path;

/** Small CLI; ontology paths are resolved relative to the configuration file. */
public class Application {
    public static void main(String... args) throws Exception {
        Path config = Path.of("scripts/java/application.yaml");
        String version = System.getProperty("soma.version");
        for (String arg : args) {
            if (arg.equals("--help")) {
                System.out.println("Usage (repository root): java -jar scripts/java/target/IsDefinedInAdder-1.0-SNAPSHOT.jar [--versionInfo=VERSION] [--config=scripts/java/application.yaml]");
                return;
            } else if (arg.startsWith("--versionInfo=")) version = arg.substring(14);
            else if (arg.startsWith("--config=")) config = Path.of(arg.substring(9));
            else throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        CIRunner.run(ConfigLoader.load(config), version);
    }
}
