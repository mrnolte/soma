package main.reasoning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import java.util.ArrayList;
import java.util.Set;

/** Versioned subprocess stdout contract. Missing checks must never default to empty findings. */
public final class ReasoningProtocol {
    private ReasoningProtocol() {}
    private static final Set<String> FIELDS = Set.of("schemaVersion", "complete", "consistent", "toolErrors",
            "unsatisfiableClasses", "emptyObjectProperties", "emptyDataProperties", "qualityFindings");

    public static ReasoningReport decode(String output) {
        try {
            var mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode root = mapper.readTree(output);
            if (root == null || !root.isObject() || root.size() != FIELDS.size())
                throw new IllegalArgumentException("Expected complete report object");
            for (String field : FIELDS) if (!root.has(field))
                throw new IllegalArgumentException("Missing field: " + field);
            if (!root.get("schemaVersion").isIntegralNumber() || !root.get("schemaVersion").canConvertToInt()
                    || root.get("schemaVersion").intValue() != 1 || !root.get("complete").isBoolean()
                    || !(root.get("consistent").isBoolean() || root.get("consistent").isNull()))
                throw new IllegalArgumentException("Invalid version or completion fields");
            return new ReasoningReport(root.get("complete").booleanValue(),
                    root.get("consistent").isNull() ? null : root.get("consistent").booleanValue(),
                    strings(root, "toolErrors"), strings(root, "unsatisfiableClasses"),
                    strings(root, "emptyObjectProperties"), strings(root, "emptyDataProperties"),
                    strings(root, "qualityFindings"));
        } catch (Exception e) {
            return ReasoningReport.toolError("Invalid reasoner output: " + e.getMessage());
        }
    }

    private static java.util.List<String> strings(JsonNode root, String field) {
        var node = root.get(field);
        if (!node.isArray()) throw new IllegalArgumentException("Expected array: " + field);
        var values = new ArrayList<String>();
        for (var value : node) {
            if (!value.isTextual()) throw new IllegalArgumentException("Expected string in " + field);
            values.add(value.textValue());
        }
        return values;
    }
}
