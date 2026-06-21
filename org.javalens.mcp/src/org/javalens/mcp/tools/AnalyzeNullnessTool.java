package org.javalens.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.javalens.core.IJdtService;
import org.javalens.mcp.models.ResponseMeta;
import org.javalens.mcp.models.ToolResponse;
import org.javalens.mcp.tools.nullness.NullnessStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Sprint 15b — null-safety analysis tool (parametric, read-only; {@code analyze_}
 * prefix → readOnlyHint). Model-free.
 *
 * <p>Kinds added across the sprint: {@code detect_style} (this stage) identifies
 * the project's nullness annotation family; {@code find_violations},
 * {@code infer_contracts}, {@code check} follow. Source-mutating operations live
 * in the separate {@code apply_null_annotations} tool (apply/undo contract).</p>
 */
public class AnalyzeNullnessTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeNullnessTool.class);

    // Grows per stage: + find_violations (C1), + infer_contracts, check (C2).
    static final Set<String> KINDS = Set.of("detect_style");

    public AnalyzeNullnessTool(Supplier<IJdtService> serviceSupplier) {
        super(serviceSupplier);
    }

    @Override
    public String getName() {
        return "analyze_nullness";
    }

    @Override
    public String getDescription() {
        return """
            Java null-safety analysis. Read-only; model-free. Source-mutating ops
            (add/migrate annotations) are in apply_null_annotations.

            USAGE: analyze_nullness(kind="detect_style")

            KINDS:
            - detect_style — identify the nullness annotation family already used in the
                             project (JSpecify / Eclipse JDT / JetBrains / JSR-305 / SpotBugs /
                             Checker / AndroidX), by import scan. Returns the dominant family,
                             per-family counts, and evidence. "none" when no family is used.

            Optional: projectKey to scope to one loaded project; maxResults. Requires load_project.
            """;
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> kind = new LinkedHashMap<>();
        kind.put("type", "string");
        kind.put("enum", KINDS);
        kind.put("description", "Which null-safety analysis to run. See the tool description.");
        properties.put("kind", kind);
        Map<String, Object> maxResults = new LinkedHashMap<>();
        maxResults.put("type", "integer");
        maxResults.put("description", "Cap on evidence entries (default 50).");
        properties.put("maxResults", maxResults);
        schema.put("properties", properties);
        schema.put("required", List.of("kind"));
        return withProjectKey(schema);
    }

    @Override
    protected ToolResponse executeWithService(IJdtService service, JsonNode arguments) {
        String kind = getStringParam(arguments, "kind");
        if (kind == null || kind.isBlank()) {
            return ToolResponse.invalidParameter("kind", "kind is required; one of " + KINDS);
        }
        if (!KINDS.contains(kind)) {
            return ToolResponse.invalidParameter("kind",
                "Unknown kind '" + kind + "'. Allowed: " + KINDS);
        }
        // Only detect_style this stage; validated above.
        return detectStyle(service, arguments);
    }

    private ToolResponse detectStyle(IJdtService service, JsonNode arguments) {
        int maxEvidence = getIntParam(arguments, "maxResults", 50);
        Map<NullnessStyle, Integer> totals = new EnumMap<>(NullnessStyle.class);
        List<String> evidence = new ArrayList<>();
        try {
            for (Path path : service.getAllJavaFiles()) {
                ICompilationUnit cu = service.getCompilationUnit(path);
                if (cu == null) {
                    continue;
                }
                ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
                parser.setSource(cu);
                parser.setKind(ASTParser.K_COMPILATION_UNIT);
                CompilationUnit ast = (CompilationUnit) parser.createAST(null);
                List<String> imports = new ArrayList<>();
                for (Object o : ast.imports()) {
                    imports.add(((ImportDeclaration) o).getName().getFullyQualifiedName());
                }
                Map<NullnessStyle, Integer> fileTally = NullnessStyle.tally(imports);
                if (!fileTally.isEmpty()) {
                    fileTally.forEach((s, n) -> totals.merge(s, n, Integer::sum));
                    if (evidence.size() < maxEvidence) {
                        evidence.add(service.getPathUtils().formatPath(path));
                    }
                }
            }
        } catch (Exception e) {
            log.error("analyze_nullness(detect_style) failed: {}", e.getMessage(), e);
            return ToolResponse.internalError(e);
        }

        NullnessStyle dominant = null;
        int best = 0;
        Map<String, Object> families = new LinkedHashMap<>();
        for (Map.Entry<NullnessStyle, Integer> e : totals.entrySet()) {
            families.put(e.getKey().name(), e.getValue());
            if (e.getValue() > best) {
                best = e.getValue();
                dominant = e.getKey();
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operation", "analyze_nullness");
        data.put("kind", "detect_style");
        data.put("detectedStyle", dominant == null ? "none" : dominant.name());
        data.put("families", families);
        data.put("evidence", evidence);
        return ToolResponse.success(data, ResponseMeta.builder()
            .suggestedNextTools(List.of(
                "analyze_nullness(kind=find_violations) to surface probable null bugs"))
            .build());
    }
}
