package main.config;

import main.converter.OWLDocumentFormatConverter;
import org.semanticweb.owlapi.model.IRI;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.nio.file.*;
import java.util.*;

/** Parse plain YAML data without constructing arbitrary Java objects. */
public final class ConfigLoader {
    private ConfigLoader() {}
    public static OntologyConfig load(Path path) throws java.io.IOException {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var input = Files.newInputStream(path)) {
            Map<?, ?> root = new Yaml(new SafeConstructor(options)).load(input);
            Map<?, ?> ontology = (Map<?, ?>) Objects.requireNonNull(root.get("ontology"), "ontology");
            var collapses = new ArrayList<CollapseConfig>();
            if (ontology.get("to-collapse") instanceof List<?> entries) {
                for (var entry : entries) {
                    var item = (Map<?, ?>) entry;
                    Object except = item.get("except");
                    List<String> exceptions = except == null ? List.of() : except instanceof List<?> list
                        ? list.stream().map(Object::toString).toList()
                        : Arrays.stream(except.toString().split(",")).map(String::trim).toList();
                    collapses.add(new CollapseConfig(item.get("ontology").toString(),
                        Path.of(item.get("out-path").toString()), exceptions,
                        item.get("new-iri") == null ? null : IRI.create(item.get("new-iri").toString())));
                }
            }
            return new OntologyConfig(Path.of(ontology.get("directory").toString()), collapses,
                ontology.get("format") == null ? null : new OWLDocumentFormatConverter().convert(ontology.get("format").toString()));
        }
    }
}
