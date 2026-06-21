package org.javalens.mcp.tools.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.javalens.core.JdtServiceImpl;
import org.javalens.mcp.fixtures.TestProjectHelper;
import org.javalens.mcp.models.ToolResponse;
import org.javalens.mcp.tools.AnalyzeJavadocsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 15a — analyze_javadocs ingest. Drives the simple-maven JavadocTargets
 * fixture: structured tags → HIGH-confidence api_contract; free-text → LOW;
 * @deprecated → its own deprecated_behavior fact; undocumented → no fact.
 */
class AnalyzeJavadocsToolTest {

    @RegisterExtension
    TestProjectHelper helper = new TestProjectHelper();
    private AnalyzeJavadocsTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdtServiceImpl service = helper.loadProject("simple-maven");
        tool = new AnalyzeJavadocsTool(() -> service);
        objectMapper = new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ingest() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("kind", "ingest");
        ToolResponse r = tool.execute(args);
        assertTrue(r.isSuccess(), () -> String.valueOf(r.getError()));
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals("ingest", data.get("kind"));
        return (List<Map<String, Object>>) data.get("facts");
    }

    private Map<String, Object> factFor(List<Map<String, Object>> facts, String symbolEndsWith, String type) {
        return facts.stream()
            .filter(f -> type.equals(f.get("type")))
            .filter(f -> String.valueOf(f.get("symbol")).endsWith(symbolEndsWith))
            .findFirst().orElse(null);
    }

    @Test
    @DisplayName("structured method doc → HIGH-confidence api_contract with @param/@return/@throws evidence")
    void structuredContract() {
        List<Map<String, Object>> facts = ingest();
        Map<String, Object> f = factFor(facts, "JavadocTargets#discountedTotal", "api_contract");
        assertNotNull(f, "discountedTotal should yield an api_contract fact: " + facts);
        assertEquals("high", f.get("confidence"));
        @SuppressWarnings("unchecked")
        List<Object> evidence = (List<Object>) f.get("evidence");
        assertNotNull(evidence);
        String joined = evidence.toString();
        assertTrue(joined.contains("@param subtotal"), joined);
        assertTrue(joined.contains("@return"), joined);
        assertTrue(joined.contains("@throws"), joined);
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) f.get("source");
        assertEquals("javadoc", source.get("kind"));
    }

    @Test
    @DisplayName("@deprecated → a separate HIGH-confidence deprecated_behavior fact")
    void deprecatedFact() {
        List<Map<String, Object>> facts = ingest();
        Map<String, Object> f = factFor(facts, "JavadocTargets#oldRound", "deprecated_behavior");
        assertNotNull(f, "oldRound should yield a deprecated_behavior fact: " + facts);
        assertEquals("high", f.get("confidence"));
        assertTrue(String.valueOf(f.get("summary")).startsWith("Deprecated:"));
    }

    @Test
    @DisplayName("free-text-only doc → LOW-confidence fact; undocumented method → no fact")
    void freeTextAndUndocumented() {
        List<Map<String, Object>> facts = ingest();
        Map<String, Object> note = factFor(facts, "JavadocTargets#invariantNote", "domain_fact");
        assertNotNull(note, "invariantNote free-text should yield a domain_fact: " + facts);
        assertEquals("low", note.get("confidence"));

        boolean undocumentedPresent = facts.stream()
            .anyMatch(f -> String.valueOf(f.get("symbol")).endsWith("JavadocTargets#undocumented"));
        assertFalse(undocumentedPresent, "undocumented method must not produce a fact");
    }

    @Test
    @DisplayName("unknown kind is rejected")
    void unknownKind() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("kind", "no_such_kind");
        ToolResponse r = tool.execute(args);
        assertFalse(r.isSuccess());
        assertEquals("INVALID_PARAMETER", r.getError().getCode());
    }
}
