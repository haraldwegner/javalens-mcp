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

    // Grows per stage: + infer_contracts, check (C2).
    static final Set<String> KINDS = Set.of("detect_style", "find_violations");

    /**
     * Compiler options enabling null analysis, overlaid on the parser (no project
     * mutation — same pattern as AnalyzeJavadocsTool.validate). Flow checks
     * (nullReference/potentialNullReference) work WITHOUT annotations; the
     * annotation-name + nullanalysis options additionally activate contract
     * checks when the project carries its nullness annotations. The annotation
     * FQNs are set per-project to the detected family (or Eclipse defaults).
     */
    private static final Map<String, String> NULL_FLOW_OPTIONS = Map.of(
        "org.eclipse.jdt.core.compiler.problem.nullReference", "warning",
        "org.eclipse.jdt.core.compiler.problem.potentialNullReference", "warning",
        "org.eclipse.jdt.core.compiler.annotation.nullanalysis", "enabled",
        "org.eclipse.jdt.core.compiler.problem.nullSpecViolation", "warning",
        "org.eclipse.jdt.core.compiler.problem.nullAnnotationInferenceConflict", "warning",
        "org.eclipse.jdt.core.compiler.problem.nullUncheckedConversion", "warning");

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
            - detect_style    — identify the nullness annotation family already used in the
                                project (JSpecify / Eclipse JDT / JetBrains / JSR-305 / SpotBugs /
                                Checker / AndroidX), by import scan. Returns the dominant family,
                                per-family counts, and evidence. "none" when no family is used.
            - find_violations — enable JDT null analysis and report probable null bugs (null
                                dereferences, nullable→non-null flows, contract mismatches).
                                Flow checks work with no annotations; contract checks activate
                                when the project carries its nullness jar. Compiler-driven.

            Optional: filePath to scope to one file; projectKey to scope to one loaded project;
            maxResults. Requires load_project.
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
        Map<String, Object> filePath = new LinkedHashMap<>();
        filePath.put("type", "string");
        filePath.put("description", "Optional. Restrict find_violations to one file; omit to scan the project.");
        properties.put("filePath", filePath);
        Map<String, Object> maxResults = new LinkedHashMap<>();
        maxResults.put("type", "integer");
        maxResults.put("description", "Cap on results (default 50 for detect_style evidence; 200 for violations).");
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
        return switch (kind) {
            case "detect_style" -> detectStyle(service, arguments);
            case "find_violations" -> findViolations(service, arguments);
            default -> ToolResponse.invalidParameter("kind", "Unhandled kind '" + kind + "'");
        };
    }

    private ToolResponse findViolations(IJdtService service, JsonNode arguments) {
        int maxResults = getIntParam(arguments, "maxResults", 200);
        String filePath = getStringParam(arguments, "filePath");

        List<Path> targets = new ArrayList<>();
        try {
            if (filePath != null && !filePath.isBlank()) {
                Path p = Path.of(filePath);
                if (service.getCompilationUnit(p) == null) {
                    return ToolResponse.fileNotFound(filePath);
                }
                targets.add(p);
            } else {
                targets.addAll(service.getAllJavaFiles());
            }

            NullnessStyle family = dominantStyle(service);

            List<Map<String, Object>> findings = new ArrayList<>();
            for (Path path : targets) {
                if (findings.size() >= maxResults) {
                    break;
                }
                ICompilationUnit cu = service.getCompilationUnit(path);
                if (cu == null) {
                    continue;
                }
                Map<String, String> opts = new java.util.HashMap<>(cu.getJavaProject().getOptions(true));
                opts.putAll(NULL_FLOW_OPTIONS);
                // Tell the compiler which annotations are nullness annotations.
                opts.put("org.eclipse.jdt.core.compiler.annotation.nullable", family.nullableFqn());
                opts.put("org.eclipse.jdt.core.compiler.annotation.nonnull", family.nonnullFqn());
                if (family == NullnessStyle.ECLIPSE) {
                    opts.put("org.eclipse.jdt.core.compiler.annotation.nonnullbydefault",
                        "org.eclipse.jdt.annotation.NonNullByDefault");
                }

                ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
                parser.setSource(cu);
                parser.setKind(ASTParser.K_COMPILATION_UNIT);
                parser.setResolveBindings(true);
                parser.setBindingsRecovery(true);
                parser.setCompilerOptions(opts);
                CompilationUnit ast = (CompilationUnit) parser.createAST(null);
                if (ast == null) {
                    continue;
                }
                String rel = service.getPathUtils().formatPath(path);
                for (org.eclipse.jdt.core.compiler.IProblem problem : ast.getProblems()) {
                    if (findings.size() >= maxResults) {
                        break;
                    }
                    String msg = problem.getMessage();
                    // Null problems have no dedicated IProblem category; their messages
                    // reliably mention "null" (null pointer access, null type mismatch,
                    // mismatching null constraints, …). Match on that — robust + compile-safe.
                    if (msg == null || !msg.toLowerCase().contains("null")) {
                        continue;
                    }
                    Map<String, Object> finding = new LinkedHashMap<>();
                    finding.put("filePath", rel);
                    finding.put("line", problem.getSourceLineNumber() - 1);
                    finding.put("severity", problem.isError() ? "error" : "warning");
                    finding.put("message", msg);
                    finding.put("problemId", problem.getID());
                    findings.add(finding);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("operation", "analyze_nullness");
            data.put("kind", "find_violations");
            data.put("nullnessStyle", family.name());
            data.put("violationCount", findings.size());
            data.put("violations", findings);
            return ToolResponse.success(data, ResponseMeta.builder()
                .totalCount(findings.size())
                .returnedCount(findings.size())
                .suggestedNextTools(List.of(
                    "apply_null_annotations(kind=add) to record a confirmed contract"))
                .build());
        } catch (Exception e) {
            log.error("analyze_nullness(find_violations) failed: {}", e.getMessage(), e);
            return ToolResponse.internalError(e);
        }
    }

    /** Dominant nullness family across the project, or the Eclipse default for option FQNs. */
    private NullnessStyle dominantStyle(IJdtService service) throws Exception {
        Map<NullnessStyle, Integer> totals = new EnumMap<>(NullnessStyle.class);
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
            NullnessStyle.tally(imports).forEach((s, n) -> totals.merge(s, n, Integer::sum));
        }
        NullnessStyle dominant = NullnessStyle.ECLIPSE; // option-FQN default (JDT-native)
        int best = 0;
        for (Map.Entry<NullnessStyle, Integer> e : totals.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                dominant = e.getKey();
            }
        }
        return dominant;
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
