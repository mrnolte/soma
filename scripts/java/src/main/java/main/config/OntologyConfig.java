package main.config;

import org.semanticweb.owlapi.model.OWLDocumentFormat;
import java.nio.file.*;
import java.util.*;

public record OntologyConfig(Path directory, Collection<CollapseConfig> toCollapse, OWLDocumentFormat format,
                             Path outputDirectory, TransformationConfig transformations) {
    public OntologyConfig {
        directory = directory.toAbsolutePath().normalize();
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
        toCollapse = List.copyOf(toCollapse);
        Objects.requireNonNull(format, "ontology.format");
        Objects.requireNonNull(transformations, "ontology.transformations");
        var names = new HashSet<Path>();
        for (var collapse : toCollapse) {
            var name = collapse.outPath();
            if (name.isAbsolute() || name.getNameCount() != 1 || !name.toString().endsWith(".owl") || !names.add(name))
                throw new IllegalArgumentException("Invalid or duplicate export filename: " + name);
        }
    }
    /** Resolve existing ancestors as well, so links cannot route output back into sources. */
    public void validatePaths() throws java.io.IOException {
        validateOutputPaths();
        if (!Files.isDirectory(directory)) throw new IllegalArgumentException("Missing ontology.directory: " + directory);
        if (!Files.isRegularFile(directory.resolve("catalog-v001.xml")))
            throw new IllegalArgumentException("Missing local import catalog in " + directory);
    }
    /** Reporting must remain possible when an input directory or its catalog has disappeared. */
    public void validateOutputPaths() throws java.io.IOException {
        var source = realDestination(directory);
        var output = realDestination(outputDirectory);
        if (output.startsWith(source) || source.startsWith(output))
            throw new IllegalArgumentException("Output directory must be separate from sources: " + output);
        if (Files.exists(outputDirectory) && !Files.isDirectory(outputDirectory))
            throw new IllegalArgumentException("Output is not a directory: " + outputDirectory);
        if (Files.isDirectory(outputDirectory)) try (var children = Files.walk(outputDirectory)) {
            List<Path> sources = List.of();
            if (Files.isDirectory(directory)) try (var inputs = Files.list(directory)) {
                sources = inputs.filter(Files::isRegularFile).toList();
            }
            for (var child : children.toList()) {
                if (Files.isSymbolicLink(child) || !child.toRealPath().startsWith(output))
                    throw new IllegalArgumentException("Output contains linked paths: " + child);
                if (Files.isRegularFile(child)) for (var input : sources)
                    if (Files.isSameFile(child, input)) throw new IllegalArgumentException("Output is hard-linked to input: " + child);
            }
        }
    }
    public Path owlDirectory() { return outputDirectory.resolve("dist/owl"); }
    public Path reportsDirectory() { return outputDirectory.resolve("reports"); }
    private static Path realDestination(Path path) throws java.io.IOException {
        if (Files.exists(path)) return path.toRealPath();
        return realDestination(path.getParent()).resolve(path.getFileName());
    }
}
