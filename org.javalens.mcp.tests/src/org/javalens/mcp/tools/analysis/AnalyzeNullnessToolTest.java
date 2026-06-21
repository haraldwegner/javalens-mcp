package org.javalens.mcp.tools.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.javalens.core.JdtServiceImpl;
import org.javalens.mcp.fixtures.TestProjectHelper;
import org.javalens.mcp.models.ToolResponse;
import org.javalens.mcp.tools.AnalyzeNullnessTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 15b — analyze_nullness detect_style. Drives the simple-maven fixture,
 * whose NullnessStyleTarget uses JSpecify annotations (source-only detection).
 */
class AnalyzeNullnessToolTest {

    @RegisterExtension
    TestProjectHelper helper = new TestProjectHelper();
    private AnalyzeNullnessTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdtServiceImpl service = helper.loadProject("simple-maven");
        tool = new AnalyzeNullnessTool(() -> service);
        objectMapper = new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detect() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("kind", "detect_style");
        ToolResponse r = tool.execute(args);
        assertTrue(r.isSuccess(), () -> String.valueOf(r.getError()));
        return (Map<String, Object>) r.getData();
    }

    @Test
    @DisplayName("detect_style identifies JSpecify as the project's family")
    void detectsJSpecify() {
        Map<String, Object> d = detect();
        assertEquals("detect_style", d.get("kind"));
        assertEquals("JSPECIFY", d.get("detectedStyle"));
        @SuppressWarnings("unchecked")
        Map<String, Object> families = (Map<String, Object>) d.get("families");
        assertTrue(families.containsKey("JSPECIFY"), "families: " + families);
        assertFalse(((java.util.List<?>) d.get("evidence")).isEmpty(), "evidence files expected");
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> findViolations() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("kind", "find_violations");
        ToolResponse r = tool.execute(args);
        assertTrue(r.isSuccess(), () -> String.valueOf(r.getError()));
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals("find_violations", data.get("kind"));
        return (java.util.List<Map<String, Object>>) data.get("violations");
    }

    @Test
    @DisplayName("find_violations reports flow null dereferences from the fixture")
    void findsNullDeref() {
        java.util.List<Map<String, Object>> v = findViolations();
        assertFalse(v.isEmpty(), "expected null-pointer findings");
        assertTrue(v.stream().allMatch(f -> String.valueOf(f.get("message")).toLowerCase().contains("null")),
            "every finding mentions null: " + v);
        assertTrue(v.stream().anyMatch(f -> String.valueOf(f.get("filePath")).endsWith("NullnessViolations.java")),
            "the deref fixture should be flagged: " + v);
    }

    @Test
    @DisplayName("unknown kind is rejected")
    void unknownKind() {
        ToolResponse r = tool.execute(objectMapper.createObjectNode().put("kind", "no_such"));
        assertFalse(r.isSuccess());
        assertEquals("INVALID_PARAMETER", r.getError().getCode());
    }
}
