package main.config;

import main.converter.OWLDocumentFormatConverter;
import org.semanticweb.owlapi.model.IRI;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.nio.file.*;
import java.util.*;

/** Plain YAML only; reject mistakes before loading any ontology. Paths are config-relative. */
public final class ConfigLoader {
    private ConfigLoader() {}
    public static OntologyConfig load(Path path) throws java.io.IOException {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        var base = path.toAbsolutePath().normalize().getParent();
        try (var input = Files.newInputStream(path)) {
            var root = map(new Yaml(new SafeConstructor(options)).load(input), "configuration", Set.of("ontology"));
            var ontology = map(root.get("ontology"), "ontology", Set.of("directory", "output-directory", "format", "to-collapse", "transformations"));
            var transforms = ontology.containsKey("transformations")
                ? map(ontology.get("transformations"), "ontology.transformations", Set.of("rewrite-gci", "rewrite-iri", "is-defined-by", "version-info")) : Map.<String, Object>of();
            var collapses = new ArrayList<CollapseConfig>();
            Object entries = ontology.getOrDefault("to-collapse", List.of());
            if (!(entries instanceof List<?> list)) throw invalid("ontology.to-collapse", "expected a list");
            for (int i = 0; i < list.size(); i++) {
                String field = "ontology.to-collapse[" + i + "]";
                var item = map(list.get(i), field, Set.of("ontology", "out-path", "except", "new-iri"));
                var exceptions = new ArrayList<String>();
                if (item.containsKey("except")) {
                    Object value = item.get("except");
                    var values = value instanceof String s ? Arrays.asList(s.split(",", -1)) : value;
                    if (!(values instanceof List<?> refs)) throw invalid(field + ".except", "expected a string or list of strings");
                    for (var ref : refs) exceptions.add(string(ref, field + ".except").trim());
                }
                IRI iri = null;
                if (item.containsKey("new-iri")) {
                    String value = string(item.get("new-iri"), field + ".new-iri");
                    if (!java.net.URI.create(value).isAbsolute()) throw invalid(field + ".new-iri", "expected an absolute IRI");
                    iri = IRI.create(value);
                }
                collapses.add(new CollapseConfig(string(item.get("ontology"), field + ".ontology"),
                    Path.of(string(item.get("out-path"), field + ".out-path")), List.copyOf(exceptions), iri));
            }
            var config = new OntologyConfig(base.resolve(string(ontology.get("directory"), "ontology.directory")), collapses,
                new OWLDocumentFormatConverter().convert(ontology.containsKey("format") ? string(ontology.get("format"), "ontology.format") : "functional"),
                base.resolve(string(ontology.get("output-directory"), "ontology.output-directory")),
                new TransformationConfig(bool(transforms, "rewrite-gci", true), bool(transforms, "rewrite-iri", false),
                    bool(transforms, "is-defined-by", true), bool(transforms, "version-info", true)));
            config.validatePaths();
            return config;
        }
    }
    private static Map<String, Object> map(Object value, String field, Set<String> allowed) {
        if (!(value instanceof Map<?, ?> values)) throw invalid(field, "expected a mapping");
        var result = new LinkedHashMap<String, Object>();
        values.forEach((key, entry) -> {
            if (!(key instanceof String name) || !allowed.contains(name)) throw invalid(field, "unknown field: " + key);
            result.put((String) key, entry);
        });
        return result;
    }
    private static String string(Object value, String field) {
        if (!(value instanceof String s) || s.isBlank()) throw invalid(field, "expected a non-empty string");
        return s;
    }
    private static boolean bool(Map<String, Object> values, String key, boolean fallback) {
        if (!values.containsKey(key)) return fallback;
        if (!(values.get(key) instanceof Boolean value)) throw invalid("ontology.transformations." + key, "expected true or false");
        return value;
    }
    private static IllegalArgumentException invalid(String field, String detail) {
        return new IllegalArgumentException("Invalid " + field + ": " + detail);
    }
}
