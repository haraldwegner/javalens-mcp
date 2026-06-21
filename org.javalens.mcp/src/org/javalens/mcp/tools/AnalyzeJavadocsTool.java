package org.javalens.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.javalens.core.IJdtService;
import org.javalens.mcp.knowledge.Confidence;
import org.javalens.mcp.knowledge.SymbolFact;
import org.javalens.mcp.models.ResponseMeta;
import org.javalens.mcp.models.ToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Sprint 15a — Javadoc knowledge tool (parametric). Read-only ({@code analyze_}
 * prefix → readOnlyHint). Model-free: it surfaces what JDT can derive, never
 * model-written prose.
 *
 * <p>Kinds (added incrementally across the sprint): {@code ingest} (this stage)
 * parses existing Javadocs into symbol-anchored {@link SymbolFact}s. Structured
 * tags ({@code @param}/{@code @return}/{@code @throws}/{@code @deprecated}/
 * {@code @see}) are machine-readable → HIGH confidence; free-text prose is
 * surfaced verbatim at LOW confidence (no semantic extraction — honest about
 * what is deterministic).</p>
 */
public class AnalyzeJavadocsTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeJavadocsTool.class);

    // Grows per stage: + validate (C2), + generate (C3).
    static final Set<String> KINDS = Set.of("ingest");

    public AnalyzeJavadocsTool(Supplier<IJdtService> serviceSupplier) {
        super(serviceSupplier);
    }

    @Override
    public String getName() {
        return "analyze_javadocs";
    }

    @Override
    public String getDescription() {
        return """
            Javadoc knowledge tool. Read-only; model-free (surfaces facts/evidence,
            never model-written prose).

            USAGE: analyze_javadocs(kind="ingest")

            KINDS:
            - ingest — parse existing Javadocs into symbol-anchored facts
                       { type, symbol, summary, details, source, confidence, evidence }.
                       Structured tags (@param/@return/@throws/@deprecated/@see) are
                       HIGH confidence; free-text prose is surfaced at LOW confidence.

            Optional: filePath to scope to one file; projectKey to scope to one loaded
            project; maxResults (default 200). Requires load_project first.
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
        kind.put("description", "Which Javadoc operation to run. See the tool description.");
        properties.put("kind", kind);
        Map<String, Object> filePath = new LinkedHashMap<>();
        filePath.put("type", "string");
        filePath.put("description", "Optional. Restrict to one file; omit to scan the project.");
        properties.put("filePath", filePath);
        Map<String, Object> maxResults = new LinkedHashMap<>();
        maxResults.put("type", "integer");
        maxResults.put("description", "Cap on facts returned (default 200).");
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
        // Only "ingest" is implemented this stage; validated above.
        return ingest(service, arguments);
    }

    private ToolResponse ingest(IJdtService service, JsonNode arguments) {
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

            List<Map<String, Object>> facts = new ArrayList<>();
            for (Path path : targets) {
                if (facts.size() >= maxResults) {
                    break;
                }
                ICompilationUnit cu = service.getCompilationUnit(path);
                if (cu == null) {
                    continue;
                }
                ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
                parser.setSource(cu);
                parser.setKind(ASTParser.K_COMPILATION_UNIT);
                parser.setResolveBindings(true);
                parser.setBindingsRecovery(true);
                CompilationUnit ast = (CompilationUnit) parser.createAST(null);
                String rel = service.getPathUtils().formatPath(path);
                collectFacts(ast, rel, facts, maxResults);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("operation", "analyze_javadocs");
            data.put("kind", "ingest");
            data.put("factCount", facts.size());
            data.put("facts", facts);
            return ToolResponse.success(data, ResponseMeta.builder()
                .totalCount(facts.size())
                .returnedCount(facts.size())
                .suggestedNextTools(List.of(
                    "analyze_type to inspect a documented type",
                    "find_references to see who depends on a documented contract"))
                .build());
        } catch (Exception e) {
            log.error("analyze_javadocs(ingest) failed: {}", e.getMessage(), e);
            return ToolResponse.internalError(e);
        }
    }

    private void collectFacts(CompilationUnit ast, String rel,
                              List<Map<String, Object>> out, int max) {
        ast.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode node) {
                if (out.size() >= max || !(node instanceof BodyDeclaration decl)) {
                    return;
                }
                Javadoc jd = decl.getJavadoc();
                if (jd == null) {
                    return;
                }
                String fqn = fqnOf(decl);

                String mainText = "";
                List<String> paramTags = new ArrayList<>();
                String returnText = null;
                List<String> throwsTags = new ArrayList<>();
                List<String> seeTags = new ArrayList<>();
                String deprecatedText = null;

                for (Object o : jd.tags()) {
                    TagElement tag = (TagElement) o;
                    String name = tag.getTagName();
                    if (name == null) {
                        mainText = render(tag.fragments());
                    } else if (TagElement.TAG_PARAM.equals(name)) {
                        paramTags.add(render(tag.fragments()));
                    } else if (TagElement.TAG_RETURN.equals(name)) {
                        returnText = render(tag.fragments());
                    } else if (TagElement.TAG_THROWS.equals(name) || TagElement.TAG_EXCEPTION.equals(name)) {
                        throwsTags.add(render(tag.fragments()));
                    } else if (TagElement.TAG_SEE.equals(name)) {
                        seeTags.add(render(tag.fragments()));
                    } else if (TagElement.TAG_DEPRECATED.equals(name)) {
                        deprecatedText = render(tag.fragments());
                    }
                }

                boolean structured = !paramTags.isEmpty() || returnText != null || !throwsTags.isEmpty();
                if (!structured && mainText.isBlank() && deprecatedText == null) {
                    return; // empty/marker-only javadoc — nothing to anchor
                }

                List<Object> evidence = new ArrayList<>();
                for (String p : paramTags) {
                    evidence.add("@param " + p);
                }
                if (returnText != null) {
                    evidence.add("@return " + returnText);
                }
                for (String t : throwsTags) {
                    evidence.add("@throws " + t);
                }
                for (String s : seeTags) {
                    evidence.add("@see " + s);
                }

                String summary = firstSentence(mainText);
                if (summary.isBlank()) {
                    summary = "Documented " + kindLabel(decl) + (fqn == null ? "" : " " + fqn);
                }
                SymbolFact.Builder fact = SymbolFact
                    .of(structured ? "api_contract" : "domain_fact", summary,
                        structured ? Confidence.HIGH : Confidence.LOW)
                    .source("javadoc", rel)
                    .evidence(evidence.isEmpty() ? null : evidence);
                if (fqn != null) {
                    fact.symbol(fqn);
                }
                if (!mainText.isBlank()) {
                    fact.details(mainText);
                }
                out.add(fact.build().toMap());

                if (deprecatedText != null && out.size() < max) {
                    SymbolFact.Builder dep = SymbolFact
                        .of("deprecated_behavior",
                            "Deprecated: " + firstSentence(deprecatedText.isBlank()
                                ? "see Javadoc" : deprecatedText),
                            Confidence.HIGH)
                        .source("javadoc", rel);
                    if (fqn != null) {
                        dep.symbol(fqn);
                    }
                    if (!deprecatedText.isBlank()) {
                        dep.details(deprecatedText);
                    }
                    out.add(dep.build().toMap());
                }
            }
        });
    }

    /** Best-effort FQN from bindings; null when unresolved. */
    private static String fqnOf(BodyDeclaration decl) {
        if (decl instanceof AbstractTypeDeclaration t) {
            ITypeBinding b = t.resolveBinding();
            return b == null ? null : b.getQualifiedName();
        }
        if (decl instanceof MethodDeclaration m) {
            IMethodBinding b = m.resolveBinding();
            if (b == null || b.getDeclaringClass() == null) {
                return null;
            }
            return b.getDeclaringClass().getQualifiedName() + "#" + b.getName();
        }
        if (decl instanceof FieldDeclaration f && !f.fragments().isEmpty()) {
            VariableDeclarationFragment frag = (VariableDeclarationFragment) f.fragments().get(0);
            IVariableBinding b = frag.resolveBinding();
            if (b == null || b.getDeclaringClass() == null) {
                return null;
            }
            return b.getDeclaringClass().getQualifiedName() + "#" + b.getName();
        }
        if (decl instanceof EnumConstantDeclaration e) {
            IVariableBinding b = e.resolveVariable();
            if (b == null || b.getDeclaringClass() == null) {
                return null;
            }
            return b.getDeclaringClass().getQualifiedName() + "#" + b.getName();
        }
        if (decl instanceof AnnotationTypeMemberDeclaration a) {
            IMethodBinding b = a.resolveBinding();
            if (b == null || b.getDeclaringClass() == null) {
                return null;
            }
            return b.getDeclaringClass().getQualifiedName() + "#" + b.getName();
        }
        return null;
    }

    private static String kindLabel(BodyDeclaration decl) {
        if (decl instanceof AbstractTypeDeclaration) {
            return "type";
        }
        if (decl instanceof MethodDeclaration) {
            return "method";
        }
        if (decl instanceof FieldDeclaration) {
            return "field";
        }
        return "symbol";
    }

    /** Render a TagElement's fragments to plain text (TextElement, names, inline tags). */
    private static String render(List<?> fragments) {
        StringBuilder sb = new StringBuilder();
        for (Object f : fragments) {
            if (f instanceof TextElement te) {
                sb.append(te.getText());
            } else if (f instanceof SimpleName sn) {
                sb.append(sn.getIdentifier());
            } else if (f instanceof Name n) {
                sb.append(n.getFullyQualifiedName());
            } else if (f instanceof MemberRef mr) {
                sb.append(mr.getName().getIdentifier());
            } else if (f instanceof MethodRef mr) {
                sb.append(mr.getName().getIdentifier());
            } else if (f instanceof TagElement nested) {
                sb.append(render(nested.fragments()));
            }
            sb.append(' ');
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String firstSentence(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        int dot = t.indexOf(". ");
        if (dot > 0) {
            return t.substring(0, dot + 1).trim();
        }
        if (t.endsWith(".")) {
            return t;
        }
        return t;
    }
}
