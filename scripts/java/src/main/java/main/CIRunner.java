package main;

import main.ci_runners.*;
import main.config.OntologyConfig;
import org.semanticweb.owlapi.model.OWLOntology;
import java.util.Map;

public final class CIRunner {
    public record Result(OntologyManager sources, OntologyManager working, Map<String, OWLOntology> exports) {}

    public static Result run(OntologyConfig config, String version) throws Exception {
        return run(config, version, "unspecified");
    }

    public static Result run(OntologyConfig config, String version, String commit) throws Exception {
        config.validatePaths();
        try {
            if (config.transformations().versionInfo() && (version == null || version.isBlank()))
                throw new IllegalArgumentException("Version is required when version-info is enabled");
            var sources = new OntologyManager();
            sources.load(config);
            var working = sources.workingCopy();
            var transforms = config.transformations();
            // One visible order: optional namespace repair, validation, GCI, provenance, version, aggregation, export.
            if (transforms.rewriteIri()) new IRINamespaceRewriter(working).run();
            new IRINamespaceChecker(working).run();
            if (transforms.rewriteGci()) new SubclassNothingRewriter(working).run();
            if (transforms.isDefinedBy()) new IsDefinedInAdder(working).run();
            if (transforms.versionInfo()) new VersionInfoAdder(working, version).run();
            var exports = new Collapser().collapse(working, config);
            new OntologySaver().save(config, working, exports, version, commit);
            return new Result(sources, working, exports);
        } catch (Exception failure) {
            try { new OntologySaver().failure(config, failure); }
            catch (Exception reportingFailure) { failure.addSuppressed(reportingFailure); }
            throw failure;
        }
    }
}
