package main;

import main.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.model.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryExportTest {
    @TempDir Path temp;

    @Test void fullRepositoryExportKeepsSourcesAndReloadsEveryModule() throws Exception {
        var baseline = ConfigLoader.load(Path.of("application.yaml"));
        var config = new OntologyConfig(baseline.directory(), baseline.toCollapse(), baseline.format(), temp.resolve("dist"), baseline.transformations());
        var bytes = new TreeMap<Path, byte[]>();
        try (var files = Files.list(config.directory())) {
            for (var path : files.toList()) if (Files.isRegularFile(path)) bytes.put(path, Files.readAllBytes(path));
        }
        var result = CIRunner.run(config, "test");
        for (var item : bytes.entrySet()) assertArrayEquals(item.getValue(), Files.readAllBytes(item.getKey()), item.getKey().toString());
        assertEquals(16, result.sources().documents().size());
        assertEquals(result.sources().documents().keySet(), result.exports().keySet());
        // Check that copied imports retain the complete source closure, including the ALL/All alias.
        for (var entry : result.sources().documents().entrySet()) {
            assertEquals(entry.getValue().getImportsClosure().size(),
                result.working().documents().get(entry.getKey()).getImportsClosure().size(), entry.getKey());
        }
        for (var request : config.toCollapse()) {
            var root = result.working().resolve(request.ontology());
            var excluded = new HashSet<OWLOntology>();
            for (var reference : request.except()) excluded.addAll(result.working().resolve(reference).getImportsClosure());
            var expected = new HashSet<OWLAxiom>();
            root.getImportsClosure().stream().filter(o -> !excluded.contains(o)).forEach(o -> expected.addAll(o.getAxioms()));
            assertEquals(expected, result.exports().get(request.outPath().toString()).getAxioms(), request.ontology());
        }
        var reload = new OntologyManager();
        reload.load(new OntologyConfig(config.outputDirectory(), List.of(), config.format(), temp.resolve("unused"), baseline.transformations()));
        assertEquals(16, reload.documents().size());
        var home = reload.resolve("http://www.ease-crc.org/ont/SOMA-HOME.owl");
        var soma = reload.resolve("http://www.ease-crc.org/ont/SOMA.owl");
        assertTrue(home.getImportsClosure().contains(soma));
        assertEquals(result.exports().get("SOMA.owl").getLogicalAxioms(), soma.getLogicalAxioms());
        assertTrue(soma.getAxiomCount() > result.sources().resolve("http://www.ease-crc.org/ont/SOMA.owl").getAxiomCount());
        for (var item : result.sources().documents().entrySet()) {
            assertFalse(item.getValue().annotations().anyMatch(a -> a.getValue().equals(
                result.sources().getOntologyManager().getOWLDataFactory().getOWLLiteral("test"))));
        }
    }
}
