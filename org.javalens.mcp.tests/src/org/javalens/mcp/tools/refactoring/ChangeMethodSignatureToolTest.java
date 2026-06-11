package org.javalens.mcp.tools.refactoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.javalens.core.JdtServiceImpl;
import org.javalens.mcp.fixtures.TestProjectHelper;
import org.javalens.mcp.models.ToolResponse;
import org.javalens.mcp.refactoring.RefactoringChangeCache;
import org.javalens.mcp.tools.ChangeMethodSignatureTool;
import org.javalens.mcp.tools.UndoRefactoringTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ChangeMethodSignatureTool under the Sprint 14b
 * auto-apply contract (temp fixture copy; on-disk verification; undo
 * round-trip).
 */
class ChangeMethodSignatureToolTest {

    @RegisterExtension
    TestProjectHelper helper = new TestProjectHelper();

    private JdtServiceImpl service;
    private RefactoringChangeCache cache;
    private ChangeMethodSignatureTool tool;
    private UndoRefactoringTool undoTool;
    private ObjectMapper objectMapper;
    private Path refactoringTargetFile;

    @BeforeEach
    void setUp() throws Exception {
        service = helper.loadProjectCopy("simple-maven");
        cache = new RefactoringChangeCache();
        tool = new ChangeMethodSignatureTool(() -> service, cache);
        undoTool = new UndoRefactoringTool(() -> service, cache);
        objectMapper = new ObjectMapper();
        refactoringTargetFile = helper.getTempDirectory()
            .resolve("simple-maven/src/main/java/com/example/RefactoringTarget.java");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(ToolResponse r) { return (Map<String, Object>) r.getData(); }

    private ObjectNode baseArgs() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("filePath", refactoringTargetFile.toString());
        args.put("line", 71);  // formatMessage method
        args.put("column", 18);
        return args;
    }

    private ObjectNode param(String name, String type) {
        ObjectNode param = objectMapper.createObjectNode();
        param.put("name", name);
        param.put("type", type);
        return param;
    }

    // ========== Auto-apply contract ==========

    @Test
    @DisplayName("renames method and updates call sites on disk; undo restores")
    void renamesMethod_appliesAndUndoRestores() throws Exception {
        String original = Files.readString(refactoringTargetFile);

        ObjectNode args = baseArgs();
        args.put("newName", "formatOutput");

        ToolResponse response = tool.execute(args);

        assertTrue(response.isSuccess(), () -> String.valueOf(response.getError()));
        Map<String, Object> data = getData(response);
        assertEquals(Boolean.TRUE, data.get("applied"));
        assertEquals("formatOutput", data.get("newName"));
        assertTrue((int) data.get("totalEdits") > 1, "declaration + call sites");
        assertNotNull(data.get("undoChangeId"));

        String onDisk = Files.readString(refactoringTargetFile);
        assertTrue(onDisk.contains("formatOutput("),
            "renamed signature and call sites must be on disk");
        assertNotEquals(original, onDisk);

        ToolResponse undone = undoTool.execute(objectMapper.createObjectNode()
            .put("undoChangeId", (String) data.get("undoChangeId")));
        assertTrue(undone.isSuccess(), () -> String.valueOf(undone.getError()));
        assertEquals(original, Files.readString(refactoringTargetFile));
    }

    @Test
    @DisplayName("adds new parameter with default value")
    void addsParameterWithDefaultValue() throws Exception {
        ObjectNode args = baseArgs();
        ArrayNode params = objectMapper.createArrayNode();
        params.add(param("message", "String"));
        params.add(param("count", "int"));
        ObjectNode withDefault = param("prefix", "String");
        withDefault.put("defaultValue", "\"\"");
        params.add(withDefault);
        args.set("newParameters", params);

        ToolResponse response = tool.execute(args);

        assertTrue(response.isSuccess(), () -> String.valueOf(response.getError()));
        Map<String, Object> data = getData(response);
        assertEquals(3, data.get("newParameterCount"));
        assertTrue(Files.readString(refactoringTargetFile).contains("String prefix"),
            "new parameter must be in the signature on disk");
    }

    @Test
    @DisplayName("removes parameter from method signature")
    void removesParameter() {
        ObjectNode args = baseArgs();
        ArrayNode params = objectMapper.createArrayNode();
        params.add(param("message", "String"));
        args.set("newParameters", params);

        ToolResponse response = tool.execute(args);

        assertTrue(response.isSuccess(), () -> String.valueOf(response.getError()));
        assertEquals(1, getData(response).get("newParameterCount"));
    }

    @Test
    @DisplayName("reorders parameters in method signature")
    void reordersParameters() {
        ObjectNode args = baseArgs();
        ArrayNode params = objectMapper.createArrayNode();
        params.add(param("count", "int"));
        params.add(param("message", "String"));
        args.set("newParameters", params);

        ToolResponse response = tool.execute(args);

        assertTrue(response.isSuccess(), () -> String.valueOf(response.getError()));
    }

    @Test
    @DisplayName("changes method return type")
    void changesReturnType() throws Exception {
        ObjectNode args = baseArgs();
        args.put("newReturnType", "void");

        ToolResponse response = tool.execute(args);

        assertTrue(response.isSuccess(), () -> String.valueOf(response.getError()));
        Map<String, Object> data = getData(response);
        assertEquals("void", data.get("newReturnType"));
        assertTrue(Files.readString(refactoringTargetFile).contains("void formatMessage")
                || Files.readString(refactoringTargetFile).contains("void formatMessage("),
            "new return type must be in the signature on disk");
    }

    // ========== Required Parameter Tests ==========

    @Test
    @DisplayName("requires at least one change to be specified — without touching disk")
    void requiresAtLeastOneChange() throws Exception {
        String original = Files.readString(refactoringTargetFile);

        ToolResponse response = tool.execute(baseArgs());

        assertFalse(response.isSuccess());
        assertEquals(original, Files.readString(refactoringTargetFile));
    }

    @Test
    @DisplayName("requires filePath parameter")
    void requiresFilePath() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("line", 71);
        args.put("column", 18);
        args.put("newName", "renamed");

        ToolResponse response = tool.execute(args);

        assertFalse(response.isSuccess());
        assertEquals("INVALID_PARAMETER", response.getError().getCode());
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("handles invalid position gracefully")
    void handlesInvalidPosition() {
        ObjectNode args = baseArgs();
        args.put("line", -1);
        args.put("column", -1);
        args.put("newName", "renamed");

        ToolResponse response = tool.execute(args);

        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("handles non-method position gracefully")
    void handlesNonMethodPosition() {
        ObjectNode args = baseArgs();
        args.put("line", 15);  // Field declaration
        args.put("column", 19);
        args.put("newName", "renamed");

        ToolResponse response = tool.execute(args);

        assertFalse(response.isSuccess());
    }
}
