package main.ci_runners;

import main.OntologyManager;
import main.config.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import java.util.*;

/** Pure in-memory aggregation. The exporter alone owns document paths and writes. */
public final class Collapser {
    public Map<String, OWLOntology> collapse(OntologyManager working, OntologyConfig config) throws Exception {
        var result = new LinkedHashMap<>(working.documents());
        var manager = OWLManager.createOWLOntologyManager();
        var outputIris = new HashSet<IRI>();
        for (var request : config.toCollapse()) {
            var root = working.resolve(request.ontology());
            var closure = root.getImportsClosure();
            var retained = new LinkedHashSet<OWLOntology>();
            for (var reference : request.except()) {
                var ontology = working.resolve(reference);
                if (ontology.equals(root) || !closure.contains(ontology))
                    throw new IllegalArgumentException("Collapse exception must be an imported ontology: " + reference);
                retained.add(ontology);
            }
            var excluded = new HashSet<OWLOntology>();
            var pending = new ArrayDeque<>(retained);
            while (!pending.isEmpty()) {
                var ontology = pending.removeFirst();
                // The root is replaced by this aggregation, so a back edge cannot supply its closure.
                if (!ontology.equals(root) && excluded.add(ontology)) pending.addAll(ontology.getDirectImports());
            }
            IRI iri = request.newIri() == null ? root.getOntologyID().getOntologyIRI().orElseThrow() : request.newIri();
            if (!outputIris.add(iri)) throw new IllegalArgumentException("Duplicate collapsed ontology IRI: " + iri);
            var collapsed = manager.createOntology(iri);
            closure.stream().filter(o -> !excluded.contains(o)).forEach(o -> collapsed.addAxioms(o.getAxioms()));
            root.annotations().forEach(a -> manager.applyChange(new AddOntologyAnnotation(collapsed, a)));
            retained.forEach(o -> manager.applyChange(new AddImport(collapsed,
                manager.getOWLDataFactory().getOWLImportsDeclaration(o.getOntologyID().getOntologyIRI().orElseThrow()))));
            String name = request.outPath().toString();
            var existing = result.get(name);
            if (existing != null && !existing.getOntologyID().getOntologyIRI().orElseThrow().equals(iri))
                throw new IllegalArgumentException("Export filename collides with another ontology: " + name);
            result.put(name, collapsed);
        }
        return Collections.unmodifiableMap(result);
    }
}
