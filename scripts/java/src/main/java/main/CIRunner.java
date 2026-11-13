package main;

import main.ci_runners.*;
import main.config.OntologyConfig;

public class CIRunner {
    public static void run(OntologyConfig config, String version) throws Exception {
        var manager = new OntologyManager(config);
        CIRunnable[] steps = {new IRINamespaceChecker(manager), new SubclassNothingRewriter(manager),
            new IsDefinedInAdder(manager), new VersionInfoAdder(manager, version),
            new Collapser(manager, config, version), new OntologySaver(manager, config)};
        for (var step : steps) step.run();
    }
}
