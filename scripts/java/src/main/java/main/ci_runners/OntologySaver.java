package main.ci_runners;

import main.OntologyManager;
import main.config.OntologyConfig;
import org.semanticweb.owlapi.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.io.StringWriter;
import javax.xml.stream.XMLOutputFactory;

/** The only processing step allowed to write. Every destination is explicit and validated. */
public final class OntologySaver {
    private static final ObjectMapper JSON = new ObjectMapper();

    public void save(OntologyConfig config, OntologyManager working, Map<String, OWLOntology> exports, String version, String commit) throws Exception {
        config.validatePaths();
        var aliases = new LinkedHashMap<>(working.aliases());
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
        if (Files.isDirectory(config.owlDirectory())) try (var files = Files.list(config.owlDirectory())) {
            for (var file : files.filter(p -> p.toString().endsWith(".owl")).toList())
                if (!exports.containsKey(file.getFileName().toString()))
                    throw new IllegalArgumentException("Unexpected stale export; use a fresh output directory: " + file);
        }
        String catalog = catalog(aliases);
        Files.createDirectories(config.owlDirectory());
        Files.createDirectories(config.outputDirectory().resolve("work"));
        Files.createDirectories(config.outputDirectory().resolve("dist/files"));
        for (var item : exports.entrySet())
            item.getValue().saveOntology(config.format(), IRI.create(config.owlDirectory().resolve(item.getKey()).toUri()));
        Files.writeString(config.owlDirectory().resolve("catalog-v001.xml"), catalog);
        // Check the actual serialized artifacts using a new manager and their own catalog.
        var reloaded = new OntologyManager();
        reloaded.load(new OntologyConfig(config.owlDirectory(), List.of(), config.format(),
            config.outputDirectory().resolve("work/reload"), config.transformations()));
        if (!exports.keySet().equals(reloaded.documents().keySet())) throw new IllegalStateException("Reloaded module set differs");
        for (var entry : exports.entrySet()) {
            var loaded = reloaded.documents().get(entry.getKey());
            // RDF/XML serialization can add explicit declarations; logical axioms must roundtrip exactly.
            if (!entry.getValue().getLogicalAxioms().equals(loaded.getLogicalAxioms()))
                throw new IllegalStateException("Logical axioms changed during export: " + entry.getKey());
            // External reasoners get self-contained documents, separate from public artifacts.
            var reasonerManager = org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager();
            var prepared = reasonerManager.createOntology(loaded.getOntologyID());
            prepared.addAxioms(loaded.axioms(org.semanticweb.owlapi.model.parameters.Imports.INCLUDED));
            prepared.saveOntology(new org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat(),
                IRI.create(config.outputDirectory().resolve("work").resolve(entry.getKey()).toUri()));
        }
        var hashes = new TreeMap<String, String>();
        try (var files = Files.list(config.owlDirectory())) {
            for (var file : files.filter(Files::isRegularFile).toList())
                hashes.put("dist/owl/" + file.getFileName(), HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        }
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("commit", commit);
        manifest.put("version", version == null ? "" : version);
        manifest.put("java", System.getProperty("java.version"));
        manifest.put("owlapi", org.semanticweb.owlapi.util.VersionInfo.getVersionInfo().getVersion());
        manifest.put("maven", System.getProperty("soma.maven", "standalone CLI"));
        manifest.put("transformations", config.transformations());
        manifest.put("sha256", hashes);
        writeJson(config.outputDirectory().resolve("manifest.json"), manifest);
        var modules = new TreeMap<String, Object>();
        for (var entry : exports.entrySet()) modules.put(entry.getKey(), Map.of(
            "iri", entry.getValue().getOntologyID().getOntologyIRI().orElseThrow().toString(),
            "axioms", entry.getValue().getAxiomCount(),
            "imports", entry.getValue().getImportsDeclarations().stream().map(d -> d.getIRI().toString()).sorted().toList()));
        Files.createDirectories(config.reportsDirectory());
        writeJson(config.reportsDirectory().resolve("processing.json"), Map.of("status", "success", "modules", modules));
    }

    public void failure(OntologyConfig config, Exception failure) throws Exception {
        config.validateOutputPaths();
        Files.createDirectories(config.reportsDirectory());
        // An unsuccessful run must not leave a previous successful manifest behind.
        Files.deleteIfExists(config.outputDirectory().resolve("manifest.json"));
        writeJson(config.reportsDirectory().resolve("processing.json"), Map.of(
            "status", "failure", "errorType", failure.getClass().getName(), "message", failure.toString()));
    }

    private static void writeJson(Path file, Object data) throws Exception {
        JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
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
