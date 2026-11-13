package main;

import main.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessingTest {
    @TempDir Path temp;
    static final String BASE = "http://example.org/";
    static final TransformationConfig NONE = new TransformationConfig(false, false, false, false);

    Path source() throws Exception {
        var source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("A.owl"), "Ontology(<" + BASE + "A.owl> Import(<" + BASE + "B.owl>) Declaration(Class(<" + BASE + "A>)))");
        Files.writeString(source.resolve("B.owl"), "Ontology(<" + BASE + "B.owl> Declaration(Class(<" + BASE + "B>)))");
        catalog(source, "<uri name=\"" + BASE + "B.owl\" uri=\"B.owl\"/>");
        return source;
    }
    void catalog(Path directory, String entries) throws Exception {
        Files.writeString(directory.resolve("catalog-v001.xml"),
            "<catalog xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\">" + entries + "</catalog>");
    }
    OntologyConfig config(Path source, TransformationConfig transformations, Collection<CollapseConfig> collapse) {
        return new OntologyConfig(source, collapse, new FunctionalSyntaxDocumentFormat(), temp.resolve("export"), transformations);
    }
    Map<String, String> snapshot(Path directory) throws Exception {
        var result = new TreeMap<String, String>();
        try (var paths = Files.list(directory)) {
            for (var path : paths.toList()) result.put(path.getFileName().toString(),
                HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        }
        return result;
    }
    @Test void preservesSourcesAndExportsReloadWithCatalog() throws Exception {
        var source = source();
        var before = snapshot(source);
        var result = CIRunner.run(config(source, NONE, List.of()), null);
        assertEquals(before, snapshot(source));
        assertEquals(result.sources().resolve(BASE + "A.owl").getAxioms(), result.exports().get("A.owl").getAxioms());
        assertNotSame(result.sources().resolve(BASE + "A.owl"), result.working().resolve(BASE + "A.owl"));
        var reloaded = new OntologyManager();
        reloaded.load(new OntologyConfig(temp.resolve("export/dist/owl"), List.of(), new FunctionalSyntaxDocumentFormat(), temp.resolve("unused"), NONE));
        assertEquals(2, reloaded.documents().size());
        assertEquals(2, reloaded.resolve(BASE + "A.owl").getImportsClosure().size());
    }
    @Test void missingImportFailsEvenIfTargetWasAlreadyLoaded() throws Exception {
        var source = source();
        // A sorts after B; B is loaded already but must still have an explicit catalog mapping.
        Files.move(source.resolve("A.owl"), source.resolve("Z.owl"));
        catalog(source, "");
        var before = snapshot(source);
        var exception = assertThrows(Exception.class, () -> CIRunner.run(config(source, NONE, List.of()), null));
        assertTrue(exception.toString().contains("Missing local catalog mapping"), exception.toString());
        assertEquals(before, snapshot(source));
        assertFalse(Files.exists(temp.resolve("export/dist/owl")));
        assertTrue(Files.readString(temp.resolve("export/reports/processing.json")).contains("failure"));
    }
    @Test void duplicateAndMissingCatalogTargetsFail() throws Exception {
        var source = source();
        String entry = "<uri name=\"" + BASE + "B.owl\" uri=\"B.owl\"/>";
        catalog(source, entry + entry);
        assertThrows(java.io.IOException.class, () -> CIRunner.run(config(source, NONE, List.of()), null));
        catalog(source, entry.replace("uri=\"B.owl\"", "uri=\"missing.owl\""));
        assertThrows(java.io.IOException.class, () -> CIRunner.run(config(source, NONE, List.of()), null));
        assertFalse(Files.exists(temp.resolve("export/dist/owl")));
    }
    @Test void validatesConfigBeforeLoading() throws Exception {
        var path = temp.resolve("config.yaml");
        for (String yaml : List.of("", "ontology: []", "ontology: {directory: 17}", "ontology: {to-collapse: wrong}",
            "ontology: {transformations: {rewrite-gci: yesplease}}", "ontology: {unknown: true}",
            "ontology: {directory: x, directory: y}")) {
            Files.writeString(path, yaml);
            assertThrows(RuntimeException.class, () -> ConfigLoader.load(path), yaml);
        }
        var source = source();
        String valid = "ontology:\n  directory: source\n  output-directory: export\n";
        Files.writeString(path, valid);
        assertEquals(source, ConfigLoader.load(path).directory());
        Files.writeString(path, valid + "  format: manchester\n");
        assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(path));
        Files.writeString(path, valid + "  to-collapse:\n    - ontology: A\n      out-path: ../A.owl\n");
        assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(path));
    }
    @Test void rejectsOutputOverlapsAndLinkedExportFiles() throws Exception {
        var source = source();
        for (var output : List.of(source, source.resolve("out"), temp)) {
            var config = new OntologyConfig(source, List.of(), new FunctionalSyntaxDocumentFormat(), output, NONE);
            assertThrows(IllegalArgumentException.class, config::validatePaths);
        }
        var output = Files.createDirectory(temp.resolve("linked-output"));
        Files.createLink(output.resolve("A.owl"), source.resolve("A.owl"));
        var config = new OntologyConfig(source, List.of(), new FunctionalSyntaxDocumentFormat(), output, NONE);
        assertThrows(IllegalArgumentException.class, config::validatePaths);
    }
    @Test void transformationsOnlyChangeWorkingAxiomsAndPreserveGciAnnotations() throws Exception {
        var source = source();
        Files.writeString(source.resolve("A.owl"), """
            Prefix(owl:=<http://www.w3.org/2002/07/owl#>)
            Prefix(rdfs:=<http://www.w3.org/2000/01/rdf-schema#>)
            Ontology(<http://example.org/A.owl>
              Import(<http://example.org/B.owl>)
              Declaration(Class(<http://example.org/A>))
              SubClassOf(Annotation(rdfs:comment "keep") ObjectIntersectionOf(<http://example.org/A> <http://example.org/B>) owl:Nothing))
            """);
        var before = snapshot(source);
        var all = new TransformationConfig(true, false, true, true);
        var result = CIRunner.run(config(source, all, List.of()), "test");
        var original = result.sources().resolve(BASE + "A.owl");
        var work = result.working().resolve(BASE + "A.owl");
        var gci = original.getGeneralClassAxioms().iterator().next();
        assertTrue(original.containsAxiom(gci));
        assertFalse(work.containsAxiom(gci));
        var df = OWLManager.getOWLDataFactory();
        var rewritten = df.getOWLSubClassOfAxiom(df.getOWLClass(IRI.create(BASE + "A")),
            df.getOWLObjectComplementOf(df.getOWLClass(IRI.create(BASE + "B"))), gci.getAnnotations());
        assertTrue(work.containsAxiom(rewritten), work.getAxioms().toString());
        var expected = new HashSet<>(original.getAxioms());
        expected.remove(gci);
        expected.add(rewritten);
        expected.add(df.getOWLAnnotationAssertionAxiom(df.getRDFSIsDefinedBy(), IRI.create(BASE + "A"), IRI.create(BASE + "A.owl")));
        assertEquals(expected, work.getAxioms());
        assertTrue(work.annotations().anyMatch(a -> a.getProperty().equals(df.getOWLVersionInfo())));
        assertEquals(before, snapshot(source));
    }
    @Test void collapseTraversesFullClosureAndRetainsExcludedImports() throws Exception {
        var source = source();
        var cfg = config(source, NONE, List.of(new CollapseConfig(BASE + "A.owl", Path.of("A.owl"), List.of(BASE + "B.owl"), null)));
        var result = CIRunner.run(cfg, null);
        var collapsed = result.exports().get("A.owl");
        assertEquals(result.sources().resolve(BASE + "A.owl").getAxioms(), collapsed.getAxioms());
        assertEquals(Set.of(OWLManager.getOWLDataFactory().getOWLImportsDeclaration(IRI.create(BASE + "B.owl"))), collapsed.getImportsDeclarations());
    }
    @Test void transformationFailureLeavesSourcesAndOutputUntouched() throws Exception {
        var source = source();
        Files.writeString(source.resolve("A.owl"), "Ontology(<http://example.org/A.owl> Declaration(Class(<http://example.org/SOMA-bad.owl#A>)))");
        // Checker applies to SOMA ontologies.
        Files.writeString(source.resolve("A.owl"), Files.readString(source.resolve("A.owl")).replace(BASE + "A.owl", BASE + "SOMA.owl"));
        var before = snapshot(source);
        assertThrows(RuntimeException.class, () -> CIRunner.run(config(source, NONE, List.of()), null));
        assertEquals(before, snapshot(source));
        assertFalse(Files.exists(temp.resolve("export/dist/owl")));
    }

    @Test void iriRepairIsExplicitAndChangesOnlyWorkingEntities() throws Exception {
        var source = source();
        String bad = "http://example.org/SOMA-bad.owl#Entity";
        Files.writeString(source.resolve("A.owl"), "Ontology(<http://example.org/SOMA.owl> Declaration(Class(<" + bad + ">)))");
        var before = snapshot(source);
        var result = CIRunner.run(config(source, new TransformationConfig(false, true, false, false), List.of()), null);
        var df = OWLManager.getOWLDataFactory();
        var original = df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(bad)));
        var repaired = df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create("http://example.org/SOMA.owl#Entity")));
        assertEquals(Set.of(original), result.sources().documents().get("A.owl").getAxioms());
        assertEquals(Set.of(repaired), result.exports().get("A.owl").getAxioms());
        assertEquals(before, snapshot(source));
    }

    @Test void versionIsRequiredOnlyWhenEnabledAndFailureInvalidatesOldManifest() throws Exception {
        var source = source();
        var cfg = config(source, NONE, List.of());
        CIRunner.run(cfg, null);
        assertTrue(Files.exists(cfg.outputDirectory().resolve("manifest.json")));
        assertTrue(Files.readString(cfg.reportsDirectory().resolve("processing.json")).contains("success"));
        var versioned = config(source, new TransformationConfig(false, false, false, true), List.of());
        assertThrows(IllegalArgumentException.class, () -> CIRunner.run(versioned, null));
        assertFalse(Files.exists(cfg.outputDirectory().resolve("manifest.json")));
        assertTrue(Files.readString(cfg.reportsDirectory().resolve("processing.json")).contains("failure"));
    }

    @Test void aggregationIncludesDeepImportsAndExcludesFullRetainedClosure() throws Exception {
        var source = source();
        Files.writeString(source.resolve("B.owl"), "Ontology(<http://example.org/B.owl> Import(<http://example.org/C.owl>) Declaration(Class(<http://example.org/B>)))");
        Files.writeString(source.resolve("C.owl"), "Ontology(<http://example.org/C.owl> Declaration(Class(<http://example.org/C>)))");
        catalog(source, "<uri name=\"http://example.org/B.owl\" uri=\"B.owl\"/><uri name=\"http://example.org/C.owl\" uri=\"C.owl\"/>");
        var keep = new CollapseConfig(BASE + "A.owl", Path.of("A.owl"), List.of(BASE + "B.owl"), null);
        var result = CIRunner.run(config(source, NONE, List.of(keep)), null);
        assertEquals(result.sources().resolve(BASE + "A.owl").getAxioms(), result.exports().get("A.owl").getAxioms());
        var merge = new CollapseConfig(BASE + "A.owl", Path.of("A.owl"), List.of(), null);
        result = CIRunner.run(config(source, NONE, List.of(merge)), null);
        assertEquals(3, result.exports().get("A.owl").getAxiomCount());
        assertTrue(result.exports().get("A.owl").getImportsDeclarations().isEmpty());
    }
}
