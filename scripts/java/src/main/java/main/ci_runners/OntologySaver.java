package main.ci_runners;

import main.OntologyManager;
import main.config.OntologyConfig;
import org.semanticweb.owlapi.model.*;
import java.nio.file.*;
import java.util.*;
import java.io.StringWriter;
import javax.xml.stream.XMLOutputFactory;

/** The only processing step allowed to write. Every destination is explicit and validated. */
public final class OntologySaver {
    public void save(OntologyConfig config, Map<String, OWLOntology> exports, Map<IRI, String> sourceAliases) throws Exception {
        config.validatePaths();
        var aliases = new LinkedHashMap<>(sourceAliases);
        var ids = new HashSet<IRI>();
        for (var item : exports.entrySet()) {
            String name = item.getKey();
            if (Path.of(name).getNameCount() != 1 || Path.of(name).isAbsolute() || !name.endsWith(".owl"))
                throw new IllegalArgumentException("Invalid export filename: " + name);
            var iri = item.getValue().getOntologyID().getOntologyIRI().orElseThrow();
            if (!ids.add(iri)) throw new IllegalArgumentException("Ambiguous export ontology IRI: " + iri);
            var previous = aliases.putIfAbsent(iri, name);
            if (previous != null && !previous.equals(name)) throw new IllegalArgumentException("Ambiguous export catalog mapping: " + iri);
        }
        for (var item : aliases.entrySet())
            if (!exports.containsKey(item.getValue())) throw new IllegalArgumentException("Missing export for " + item.getKey());
        for (var ontology : exports.values())
            for (var declaration : ontology.getImportsDeclarations())
                if (!aliases.containsKey(declaration.getIRI())) throw new IllegalArgumentException("Missing export import: " + declaration.getIRI());
        String catalog = catalog(aliases);
        Files.createDirectories(config.outputDirectory());
        for (var item : exports.entrySet())
            item.getValue().saveOntology(config.format(), IRI.create(config.outputDirectory().resolve(item.getKey()).toUri()));
        Files.writeString(config.outputDirectory().resolve("catalog-v001.xml"), catalog);
    }

    private static String catalog(Map<IRI, String> aliases) throws Exception {
        var text = new StringWriter();
        var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(text);
        xml.writeStartDocument("UTF-8", "1.0");
        xml.writeStartElement("catalog");
        xml.writeDefaultNamespace("urn:oasis:names:tc:entity:xmlns:xml:catalog");
        for (var entry : aliases.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            xml.writeEmptyElement("uri");
            xml.writeAttribute("name", entry.getKey().toString());
            xml.writeAttribute("uri", entry.getValue());
        }
        xml.writeEndElement();
        xml.writeEndDocument();
        xml.close();
        return text.toString();
    }
}
