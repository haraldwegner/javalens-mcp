package org.javalens.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.javalens.core.IJdtService;
import org.javalens.core.LoadedProject;
import org.javalens.mcp.models.ResponseMeta;
import org.javalens.mcp.models.ToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 14 Phase B.3 (v1.8.0) — {@code find_duplicate_code}: scan every
 * loaded method body, normalize the JDT token stream (identifiers and
 * literals collapsed to their kind), group methods sharing the same
 * normalized sequence as a clone group. Catches the IntelliJ-CPD style
 * "structurally-identical methods with different identifier names" pattern.
 *
 * <p>v1.8.0 MVP runs exact-match detection (similarity 1.0). The schema
 * accepts a {@code threshold} param but the only honoured value is 1.0;
 * fuzzy n-gram Jaccard matching is reserved for v1.8.1.</p>
 *
 * <h2>Normalization rules</h2>
 *
 * <ul>
 *   <li>Whitespace + comments: dropped.</li>
 *   <li>Identifiers (variables, parameters, fields, methods, types):
 *       collapsed to {@code ID}.</li>
 *   <li>String literals: collapsed to {@code STR}.</li>
 *   <li>Integer literals (int / long): {@code INT}.</li>
 *   <li>Floating-point literals (float / double): {@code FLT}.</li>
 *   <li>Character literals: {@code CHAR}.</li>
 *   <li>{@code null}: {@code NULL}.</li>
 *   <li>{@code true} / {@code false}: {@code BOOL}.</li>
 *   <li>Everything else (operators, keywords, punctuation): kept verbatim.</li>
 * </ul>
 *
 * <h2>Output</h2>
 *
 * <pre>{@code
 * {
 *   operation: "find_duplicate_code",
 *   groupCount: N,
 *   groups: [{
 *     instances: [{
 *       filePath, line, methodName, tokenCount, similarity, sourceProject
 *     }, …]
 *   }, …]
 * }
 * }</pre>
 */
public class FindDuplicateCodeTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(FindDuplicateCodeTool.class);

    private static final int DEFAULT_MIN_TOKENS = 50;
    private static final double DEFAULT_THRESHOLD = 1.0;
    private static final boolean DEFAULT_CROSS_PROJECT = true;

    public FindDuplicateCodeTool(Supplier<IJdtService> serviceSupplier) {
        super(serviceSupplier);
    }

    @Override
    public String getName() {
        return "find_duplicate_code";
    }

    @Override
    public String getDescription() {
        return """
            Find clone groups of structurally-similar methods across the
            workspace via JDT-token-stream normalization. Catches the
            IntelliJ-CPD-style "same code shape, different identifier names"
            pattern that text search and grep miss.

            USAGE:
              find_duplicate_code()
              find_duplicate_code(projectKey="core")
              find_duplicate_code(minTokens=80)
              find_duplicate_code(crossProject=false)

            Inputs:
            - projectKey — optional. Restrict to a single loaded project.
            - minTokens — default 50. A method must produce at least this
              many normalized tokens to be considered. Filters out
              one-liner setters/getters that match trivially.
            - threshold — default 1.0. v1.8.0 honours 1.0 only (exact
              normalized-token-sequence match). Fuzzy n-gram Jaccard
              matching (≥0.85 similarity) is reserved for v1.8.1.
            - crossProject — default true. When false, clones are only
              grouped within a single project (cross-project clones not
              surfaced).

            Normalization:
            - Identifiers (locals, params, fields, methods, types) → ID
            - String literals → STR, ints → INT, floats → FLT,
              chars → CHAR, null → NULL, true/false → BOOL
            - Operators / keywords / punctuation kept verbatim
            - Whitespace + comments dropped

            Output groups contain ≥2 method instances with identical
            normalized token sequence (similarity 1.0). Empty groups list
            means no clones above minTokens were found.
            """;
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("minTokens", Map.of(
            "type", "integer",
            "description", "Minimum normalized-token count per method (default 50)."));
        properties.put("threshold", Map.of(
            "type", "number",
            "description", "Match threshold; v1.8.0 honours 1.0 only (exact match). Default 1.0."));
        properties.put("crossProject", Map.of(
            "type", "boolean",
            "description", "When false, group clones only within a single project. Default true."));
        schema.put("properties", properties);
        return withProjectKey(schema);
    }

    @Override
    protected ToolResponse executeWithService(IJdtService service, JsonNode arguments) {
        int minTokens = getIntParam(arguments, "minTokens", DEFAULT_MIN_TOKENS);
        double threshold = arguments != null && arguments.has("threshold")
            ? arguments.get("threshold").asDouble(DEFAULT_THRESHOLD)
            : DEFAULT_THRESHOLD;
        boolean crossProject = getBooleanParam(arguments, "crossProject", DEFAULT_CROSS_PROJECT);

        if (threshold < 1.0) {
            log.debug("threshold={} requested; v1.8.0 MVP honours 1.0 only — proceeding with exact match",
                threshold);
        }

        // projectKey handled locally (not via AbstractTool scoping) because
        // the no-projectKey case needs the full multi-project view.
        String projectKey = getStringParam(arguments, "projectKey");
        Collection<LoadedProject> projects;
        if (projectKey != null && !projectKey.isBlank()) {
            Optional<LoadedProject> scoped = service.getProject(projectKey);
            if (scoped.isEmpty()) {
                Optional<Long> dropped = service.wasRecentlyDropped(projectKey);
                if (dropped.isPresent()) {
                    return ToolResponse.projectKeyDropped(projectKey, dropped.get());
                }
                return ToolResponse.invalidParameter("projectKey",
                    "Unknown projectKey '" + projectKey + "'. Use list_projects.");
            }
            projects = List.of(scoped.get());
        } else {
            projects = service.allProjects();
        }

        try {
            List<Map<String, Object>> groups;
            if (crossProject) {
                // Pool every method into one bucket; group by normalized sequence.
                Map<String, List<MethodFingerprint>> pool = new HashMap<>();
                for (LoadedProject lp : projects) {
                    collectFingerprints(pool, lp, service, minTokens);
                }
                groups = buildGroups(pool);
            } else {
                // Per-project pools; clones must share a project to be grouped.
                groups = new ArrayList<>();
                for (LoadedProject lp : projects) {
                    Map<String, List<MethodFingerprint>> pool = new HashMap<>();
                    collectFingerprints(pool, lp, service, minTokens);
                    groups.addAll(buildGroups(pool));
                }
            }

            groups.sort(Comparator.<Map<String, Object>>comparingInt(g ->
                -((List<?>) g.get("instances")).size())
                .thenComparing(g -> (String) firstInstance(g).get("filePath")));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("operation", "find_duplicate_code");
            data.put("groupCount", groups.size());
            data.put("groups", groups);

            return ToolResponse.success(data, ResponseMeta.builder()
                .totalCount(groups.size())
                .returnedCount(groups.size())
                .build());
        } catch (Exception e) {
            log.warn("find_duplicate_code threw unexpectedly: {}", e.getMessage(), e);
            return ToolResponse.internalError(e);
        }
    }

    private static Map<String, Object> firstInstance(Map<String, Object> group) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> instances = (List<Map<String, Object>>) group.get("instances");
        return instances.isEmpty() ? Map.of("filePath", "") : instances.get(0);
    }

    private static List<Map<String, Object>> buildGroups(Map<String, List<MethodFingerprint>> pool) {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (List<MethodFingerprint> bucket : pool.values()) {
            if (bucket.size() < 2) continue;
            List<Map<String, Object>> instances = new ArrayList<>();
            for (MethodFingerprint fp : bucket) {
                Map<String, Object> inst = new LinkedHashMap<>();
                inst.put("filePath", fp.filePath);
                inst.put("line", fp.line);
                inst.put("methodName", fp.methodName);
                inst.put("tokenCount", fp.tokenCount);
                inst.put("similarity", 1.0);
                inst.put("sourceProject", fp.sourceProject);
                instances.add(inst);
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("instances", instances);
            groups.add(group);
        }
        return groups;
    }

    private static void collectFingerprints(Map<String, List<MethodFingerprint>> sink,
                                            LoadedProject lp,
                                            IJdtService service,
                                            int minTokens) {
        IJavaProject jp = lp.javaProject();
        try {
            for (IPackageFragmentRoot root : jp.getPackageFragmentRoots()) {
                if (root.getKind() != IPackageFragmentRoot.K_SOURCE) continue;
                for (IJavaElement child : root.getChildren()) {
                    if (!(child instanceof IPackageFragment pkg)) continue;
                    for (ICompilationUnit cu : pkg.getCompilationUnits()) {
                        for (IType type : cu.getAllTypes()) {
                            for (IMethod method : type.getMethods()) {
                                MethodFingerprint fp = fingerprint(method, lp, service);
                                if (fp != null && fp.tokenCount >= minTokens) {
                                    sink.computeIfAbsent(fp.normalizedSeq,
                                        k -> new ArrayList<>()).add(fp);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error collecting fingerprints for project '{}': {}",
                lp.projectKey(), e.getMessage());
        }
    }

    private static MethodFingerprint fingerprint(IMethod method, LoadedProject lp,
                                                  IJdtService service) {
        try {
            String source = method.getSource();
            if (source == null || source.isBlank()) return null;
            StringBuilder seq = new StringBuilder();
            int tokenCount = countAndNormalize(source.toCharArray(), seq);
            if (tokenCount == 0) return null;

            MethodFingerprint fp = new MethodFingerprint();
            fp.normalizedSeq = seq.toString();
            fp.tokenCount = tokenCount;
            fp.methodName = method.getElementName();
            fp.sourceProject = lp.projectKey();
            try {
                java.nio.file.Path absolute = method.getResource().getLocation().toFile().toPath();
                fp.filePath = service.getPathUtils().formatPath(absolute);
            } catch (Exception ignore) {
                fp.filePath = method.getResource() != null
                    ? method.getResource().getName() : method.getElementName();
            }
            // Approximate line number from the method's source-range start.
            // method.getSourceRange().getOffset() is char-offset; cheap to
            // map by scanning the CU once, but we don't have the CU char
            // array here. As a fallback, use 0 — the agent can re-query
            // via search_symbols if needed.
            fp.line = approximateLine(method);
            return fp;
        } catch (Exception e) {
            log.debug("Failed to fingerprint method {}: {}",
                method.getElementName(), e.getMessage());
            return null;
        }
    }

    private static int approximateLine(IMethod method) {
        try {
            String cuSource = method.getCompilationUnit().getSource();
            int offset = method.getSourceRange().getOffset();
            if (cuSource == null || offset < 0) return 0;
            int line = 0;
            for (int i = 0; i < offset && i < cuSource.length(); i++) {
                if (cuSource.charAt(i) == '\n') line++;
            }
            return line;
        } catch (Exception ignore) {
            return 0;
        }
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "\"(?:\\\\.|[^\"\\\\])*\"" +                                            // string literal
        "|'(?:\\\\.|[^'\\\\])*'" +                                              // char literal
        "|\\d+\\.\\d+(?:[eE][+-]?\\d+)?[fFdD]?|\\.\\d+(?:[eE][+-]?\\d+)?[fFdD]?" + // float literal
        "|\\d+[eE][+-]?\\d+[fFdD]?" +                                           // float scientific without dot
        "|\\d+[lL]?" +                                                          // int / long literal
        "|[a-zA-Z_$][a-zA-Z0-9_$]*" +                                           // identifier / keyword
        "|<<=|>>>=|>>=|<<|>>>|>>|<=|>=|==|!=|&&|\\|\\||\\+\\+|--|->|::" +       // multi-char operators
        "|[+\\-*/%&|^~?:;,(){}\\[\\]<>=!.@]"                                    // single-char operators/punctuation
    );

    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "continue", "default", "do", "double",
        "else", "enum", "extends", "final", "finally", "float", "for",
        "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while",
        "var", "yield", "record", "sealed", "permits"
        // "non-sealed" is hyphenated; handled by the operator branch via "-" between two ID tokens.
    );

    /**
     * Regex-based tokenizer. JDT's {@code IScanner} would be ideal but is in
     * {@code org.eclipse.jdt.core.compiler}, which the headless Tycho test
     * runtime fails to resolve at OSGi load — Sprint 14 Stage 10 noted this
     * during integration. Regex tokenization is portable, fast enough, and
     * sufficient for clone detection (the normalization step throws away
     * exactness anyway).
     */
    private static int countAndNormalize(char[] source, StringBuilder seq) {
        String src = stripComments(new String(source));
        Matcher m = TOKEN_PATTERN.matcher(src);
        int count = 0;
        while (m.find()) {
            String tok = m.group();
            String norm = normalize(tok);
            if (norm == null) continue;
            if (seq.length() > 0) seq.append(' ');
            seq.append(norm);
            count++;
        }
        return count;
    }

    private static String stripComments(String src) {
        // Block comments first (handles Javadoc), then line comments.
        String noBlock = src.replaceAll("/\\*[\\s\\S]*?\\*/", " ");
        return noBlock.replaceAll("//[^\\n]*", " ");
    }

    private static String normalize(String token) {
        if (token.isEmpty()) return null;
        char first = token.charAt(0);
        if (first == '"') return "STR";
        if (first == '\'') return "CHAR";
        if (Character.isDigit(first) || (first == '.' && token.length() > 1 && Character.isDigit(token.charAt(1)))) {
            char last = token.charAt(token.length() - 1);
            if (token.contains(".") || token.contains("e") || token.contains("E")
                || last == 'f' || last == 'F' || last == 'd' || last == 'D') {
                return "FLT";
            }
            return "INT";
        }
        if (Character.isJavaIdentifierStart(first)) {
            if ("null".equals(token)) return "NULL";
            if ("true".equals(token) || "false".equals(token)) return "BOOL";
            if (JAVA_KEYWORDS.contains(token)) return token;
            return "ID";
        }
        // Operator / punctuation: keep verbatim
        return token;
    }

    private static final class MethodFingerprint {
        String normalizedSeq;
        int tokenCount;
        String methodName;
        String filePath;
        int line;
        String sourceProject;
    }
}
