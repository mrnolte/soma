package main;

import main.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.HermiT.ReasonerFactory;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CompatibilityTest {
    @TempDir Path directory;
    @Test void yamlConfiguration() throws Exception {
        var config = ConfigLoader.load(Path.of("application.yaml"));
        assertEquals(2, config.toCollapse().size());
        assertEquals(java.util.List.of("http://www.ease-crc.org/ont/DUL.owl", "http://www.ease-crc.org/ont/SOMA.owl"), new java.util.ArrayList<>(config.toCollapse()).get(1).except());
        assertEquals(IRI.create("http://www.ease-crc.org/ont/SOMA.owl"), config.toCollapse().iterator().next().newIri());
        assertNotNull(config.format());
    }
    @Test void loadRepositoryWithLocalCatalog() throws Exception {
        var config = ConfigLoader.load(Path.of("application.yaml"));
        var sources = new OntologyManager();
        sources.load(config);
        var manager = sources.getOntologyManager();
        try (var files = Files.list(config.directory())) {
            assertEquals(files.filter(path -> path.toString().endsWith(".owl")).count(), manager.ontologies().count());
        }
    }
    @Test void ontologyRoundtripAndReasoners() throws Exception {
        var manager = OWLManager.createOWLOntologyManager();
        var ontology = manager.createOntology(IRI.create("urn:compatibility"));
        var df = manager.getOWLDataFactory();
        var a = df.getOWLClass(IRI.create("urn:A"));
        var b = df.getOWLClass(IRI.create("urn:B"));
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(a, b));
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(b, df.getOWLNothing()));
        var file = directory.resolve("test.owl");
        manager.saveOntology(ontology, new FunctionalSyntaxDocumentFormat(), IRI.create(file.toUri()));
        var loaded = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(file.toFile());
        for (var factory : java.util.List.of(new ReasonerFactory())) {
            var reasoner = factory.createReasoner(loaded);
            try {
                assertTrue(reasoner.isConsistent());
                assertTrue(reasoner.getEquivalentClasses(a).contains(b) || reasoner.getSuperClasses(a, false).containsEntity(b));
                if (factory instanceof ReasonerFactory) assertFalse(reasoner.isSatisfiable(a));
            } finally { reasoner.dispose(); }
        }
    }
}
