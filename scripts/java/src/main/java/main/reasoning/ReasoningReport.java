package main.reasoning;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Complete means consistency and, when consistent, all three entity checks finished. */
public record ReasoningReport(boolean complete, Boolean consistent, List<String> toolErrors,
        List<String> unsatisfiableClasses, List<String> emptyObjectProperties,
        List<String> emptyDataProperties, List<String> qualityFindings) {
    private static final String OWL = "http://www.w3.org/2002/07/owl#";
    public enum Status { TOOL_ERROR, INCONSISTENT, FINDINGS, PASS }

    public ReasoningReport {
        toolErrors = normalized(toolErrors, null);
        unsatisfiableClasses = normalized(unsatisfiableClasses, OWL + "Nothing");
        emptyObjectProperties = normalized(emptyObjectProperties, OWL + "bottomObjectProperty");
        emptyDataProperties = normalized(emptyDataProperties, OWL + "bottomDataProperty");
        qualityFindings = normalized(qualityFindings, null);
        if (complete && consistent == null) throw new IllegalArgumentException("Missing consistency result");
        if (!Boolean.TRUE.equals(consistent) && (!unsatisfiableClasses.isEmpty()
                || !emptyObjectProperties.isEmpty() || !emptyDataProperties.isEmpty()))
            throw new IllegalArgumentException("Entity findings require a consistent ontology");
    }

    private static List<String> normalized(List<String> values, String excluded) {
        var result = new TreeSet<String>();
        for (String value : Objects.requireNonNull(values)) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Blank finding");
            if (!value.equals(excluded)) result.add(value);
        }
        return List.copyOf(result);
    }

    public Status status() {
        if (!complete || consistent == null || !toolErrors.isEmpty()) return Status.TOOL_ERROR;
        if (!consistent) return Status.INCONSISTENT;
        if (!unsatisfiableClasses.isEmpty() || !emptyObjectProperties.isEmpty()
                || !emptyDataProperties.isEmpty() || !qualityFindings.isEmpty()) return Status.FINDINGS;
        return Status.PASS;
    }

    public int exitCode() {
        return switch (status()) { case PASS -> 0; case TOOL_ERROR -> 2; default -> 1; };
    }

    public static ReasoningReport toolError(String message) {
        return new ReasoningReport(false, null, List.of(message), List.of(), List.of(), List.of(), List.of());
    }
}
