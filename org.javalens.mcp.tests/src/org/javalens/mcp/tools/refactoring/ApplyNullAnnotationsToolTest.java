package org.javalens.mcp.tools.refactoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.javalens.core.JdtServiceImpl;
import org.javalens.mcp.fixtures.TestProjectHelper;
import org.javalens.mcp.models.ToolResponse;
import org.javalens.mcp.refactoring.RefactoringChangeCache;
import org.javalens.mcp.tools.ApplyNullAnnotationsTool;
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
 * Sprint 15b — apply_null_annotations add under the auto-apply contract (temp
 * copy; on-disk verification; undo round-trip; public-API guard).
 */
class ApplyNullAnnotationsToolTest {

    @RegisterExtension
    TestProjectHelper helper = new TestProjectHelper();

    private JdtServiceImpl service;
    private ApplyNullAnnotationsTool tool;
    private UndoRefactoringTool undoTool;
    private ObjectMapper objectMapper;
    private Path targetFile;

    @BeforeEach
    void setUp() throws Exception {
        service = helper.loadProjectCopy("simple-maven");
        RefactoringChangeCache cache = new RefactoringChangeCache();
        tool = new ApplyNullAnnotationsTool(() -> service, cache);
        undoTool = new UndoRefactoringTool(() -> service, cache);
        objectMapper = new ObjectMapper();
        targetFile = helper.getTempDirectory()
            .resolve("simple-maven/src/main/java/com/example/AddNullTarget.java");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(ToolResponse r) { return (Map<String, Object>) r.getData(); }

    @Test
    @DisplayName("add @Nullable to a package-private param: on disk (JSpecify default) + undo restores")
    void addAndUndo() throws Exception {
        String original = Files.readString(targetFile);

        ObjectNode args = objectMapper.createObjectNode();
        args.put("kind", "add");
        args.put("symbol", "com.example.AddNullTarget#find");
        args.put("nullness", "nullable");
        args.put("parameter", "key");
        ToolResponse r = tool.execute(args);
        assertTrue(r.isSuccess(), () -> String.valueOf(r.getError()));
        Map<String, Object> data = getData(r);
        assertEquals(Boolean.TRUE, data.get("applied"));
        assertNotNull(data.get("undoChangeId"));

        String after = Files.readString(targetFile);
        assertTrue(after.contains("@Nullable"), "annotation added:\n" + after);
        assertTrue(after.contains("import org.jspecify.annotations.Nullable;"),
            "JSpecify import added (project default):\n" + after);

        ToolResponse undone = undoTool.execute(objectMapper.createObjectNode()
            .put("undoChangeId", (String) data.get("undoChangeId")));
        assertTrue(undone.isSuccess(), () -> String.valueOf(undone.getError()));
        assertEquals(original, Files.readString(targetFile), "undo restores byte-for-byte");
    }

    @Test
    @DisplayName("public target is refused without allowPublicApi, accepted with it")
    void publicApiGuard() throws Exception {
        ObjectNode refused = objectMapper.createObjectNode();
        refused.put("kind", "add");
        refused.put("symbol", "com.example.AddNullTarget#pub");
        refused.put("nullness", "nullable");
        refused.put("parameter", "x");
        ToolResponse r = tool.execute(refused);
        assertFalse(r.isSuccess(), "public target must be refused by default");
        assertEquals("REFACTORING_REFUSED", r.getError().getCode());

        ObjectNode allowed = refused.deepCopy();
        allowed.put("allowPublicApi", true);
        ToolResponse r2 = tool.execute(allowed);
        assertTrue(r2.isSuccess(), () -> String.valueOf(r2.getError()));
        assertTrue(Files.readString(targetFile).contains("@Nullable"));
    }

    @Test
    @DisplayName("missing required params rejected")
    void missingParams() {
        assertFalse(tool.execute(objectMapper.createObjectNode().put("kind", "add")).isSuccess());
        assertFalse(tool.execute(objectMapper.createObjectNode()
            .put("kind", "add").put("symbol", "com.example.AddNullTarget#find")).isSuccess());
    }
}
