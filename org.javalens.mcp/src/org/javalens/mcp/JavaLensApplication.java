package org.javalens.mcp;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.javalens.mcp.protocol.McpProtocolHandler;
import org.javalens.core.IJdtService;
import org.javalens.core.workspace.WorkspaceFileWatcher;
import org.javalens.mcp.refactoring.RefactoringChangeCache;
import org.javalens.mcp.tools.AddProjectTool;
import org.javalens.mcp.tools.ApplyRefactoringTool;
import org.javalens.mcp.tools.InspectRefactoringTool;
import org.javalens.mcp.tools.ReplaceDuplicatesTool;
import org.javalens.mcp.tools.UndoRefactoringTool;
import org.javalens.mcp.tools.HealthCheckTool;
import org.javalens.mcp.tools.ListProjectsTool;
import org.javalens.mcp.tools.LoadProjectTool;
import org.javalens.mcp.tools.RemoveProjectTool;
import org.javalens.mcp.tools.SearchSymbolsTool;
import org.javalens.mcp.tools.GoToDefinitionTool;
import org.javalens.mcp.tools.FindReferencesTool;
import org.javalens.mcp.tools.FindImplementationsTool;
import org.javalens.mcp.tools.GetTypeHierarchyTool;
import org.javalens.mcp.tools.GetDocumentSymbolsTool;
import org.javalens.mcp.tools.GetTypeMembersTool;
import org.javalens.mcp.tools.GetClasspathInfoTool;
import org.javalens.mcp.tools.GetProjectStructureTool;
import org.javalens.mcp.tools.GetSymbolInfoTool;
import org.javalens.mcp.tools.GetTypeAtPositionTool;
import org.javalens.mcp.tools.GetMethodAtPositionTool;
import org.javalens.mcp.tools.GetFieldAtPositionTool;
import org.javalens.mcp.tools.GetHoverInfoTool;
import org.javalens.mcp.tools.GetJavadocTool;
import org.javalens.mcp.tools.GetSignatureHelpTool;
import org.javalens.mcp.tools.GetEnclosingElementTool;
import org.javalens.mcp.tools.GetSuperMethodTool;
import org.javalens.mcp.tools.GetDiagnosticsTool;
import org.javalens.mcp.tools.ValidateSyntaxTool;
import org.javalens.mcp.tools.GetCallHierarchyIncomingTool;
import org.javalens.mcp.tools.GetCallHierarchyOutgoingTool;
import org.javalens.mcp.tools.FindFieldWritesTool;
import org.javalens.mcp.tools.FindTestsTool;
import org.javalens.mcp.tools.FindPatternUsagesTool;
import org.javalens.mcp.tools.FindQualityIssueTool;
import org.javalens.mcp.tools.RenameSymbolTool;
import org.javalens.mcp.tools.OrganizeImportsTool;
import org.javalens.mcp.tools.ExtractVariableTool;
import org.javalens.mcp.tools.ExtractMethodTool;
import org.javalens.mcp.tools.FindMethodReferencesTool;
import org.javalens.mcp.tools.MoveClassTool;
import org.javalens.mcp.tools.MovePackageTool;
import org.javalens.mcp.tools.PullUpTool;
import org.javalens.mcp.tools.PushDownTool;
import org.javalens.mcp.tools.EncapsulateFieldTool;
import org.javalens.mcp.tools.CompileWorkspaceTool;
import org.javalens.mcp.tools.RefreshWorkspaceTool;
import org.javalens.mcp.tools.FindDuplicateCodeTool;
import org.javalens.mcp.tools.AnalyzeJavadocsTool;
import org.javalens.mcp.tools.AnalyzeNamingTool;
import org.javalens.mcp.tools.ApplyCleanupTool;
import org.javalens.mcp.tools.FindModernizationTool;
import org.javalens.mcp.tools.RunTestsTool;
import org.javalens.mcp.tools.build.AddDependencyTool;
import org.javalens.mcp.tools.build.FindUnusedDependenciesTool;
import org.javalens.mcp.tools.build.UpdateDependencyTool;
import org.javalens.mcp.tools.codegen.GenerateConstructorTool;
import org.javalens.mcp.tools.codegen.GenerateEqualsHashCodeTool;
import org.javalens.mcp.tools.codegen.GenerateGettersSettersTool;
import org.javalens.mcp.tools.codegen.GenerateTestSkeletonTool;
import org.javalens.mcp.tools.codegen.GenerateToStringTool;
import org.javalens.mcp.tools.codegen.OverrideMethodsTool;
import org.javalens.mcp.tools.workflow.FormatTool;
import org.javalens.mcp.tools.workflow.OptimizeImportsWorkspaceTool;
import org.javalens.mcp.tools.AnalyzeFileTool;
import org.javalens.mcp.tools.AnalyzeTypeTool;
import org.javalens.mcp.tools.AnalyzeMethodTool;
import org.javalens.mcp.tools.GetTypeUsageSummaryTool;
import org.javalens.mcp.tools.ExtractConstantTool;
import org.javalens.mcp.tools.InlineVariableTool;
import org.javalens.mcp.tools.InlineMethodTool;
import org.javalens.mcp.tools.ChangeMethodSignatureTool;
import org.javalens.mcp.tools.ExtractInterfaceTool;
import org.javalens.mcp.tools.ConvertAnonymousToLambdaTool;
import org.javalens.mcp.tools.SuggestImportsTool;
import org.javalens.mcp.tools.GetQuickFixesTool;
import org.javalens.mcp.tools.ApplyQuickFixTool;
import org.javalens.mcp.tools.GetComplexityMetricsTool;
import org.javalens.mcp.tools.GetDependencyGraphTool;
import org.javalens.mcp.tools.AnalyzeChangeImpactTool;
import org.javalens.mcp.tools.AnalyzeControlFlowTool;
import org.javalens.mcp.tools.AnalyzeDataFlowTool;
import org.javalens.mcp.tools.GetDiRegistrationsTool;
import org.javalens.mcp.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.javalens.core.JdtServiceImpl;
import org.javalens.mcp.transport.HttpTransport;
import org.javalens.mcp.transport.StdioTransport;
import org.javalens.mcp.transport.TokenGenerator;
import org.javalens.mcp.transport.Transport;
import org.javalens.mcp.transport.TransportConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * OSGi application entry point for JavaLens MCP server.
 * Reads JSON-RPC messages from stdin and writes responses to stdout.
 *
 * <p>Session isolation is handled by the JavaLensLauncher wrapper which
 * injects a unique UUID into the workspace path before OSGi starts.
 */
public class JavaLensApplication implements IApplication {

    private static final Logger log = LoggerFactory.getLogger(JavaLensApplication.class);

    private volatile IJdtService jdtService;
    private volatile ProjectLoadingState loadingState = ProjectLoadingState.NOT_LOADED;
    private volatile String loadingError = null;
    // Sprint 14b: session-scoped cache backing the refactoring apply/undo
    // contract (staged Changes + undo handles, TTL + LRU bounded).
    private final RefactoringChangeCache refactoringChangeCache = new RefactoringChangeCache();
    private ToolRegistry toolRegistry;
    private McpProtocolHandler protocolHandler;
    private volatile WorkspaceFileWatcher workspaceWatcher;
    private volatile Transport activeTransport;

    // Static instance for loading state access by tools
    private static volatile JavaLensApplication instance;

    /**
     * Get the current project loading state.
     * Used by tools to provide appropriate feedback when project is loading.
     */
    public static ProjectLoadingState getLoadingState() {
        JavaLensApplication app = instance;
        return app != null ? app.loadingState : ProjectLoadingState.NOT_LOADED;
    }

    /**
     * Get the loading error message if loading failed.
     */
    public static String getLoadingError() {
        JavaLensApplication app = instance;
        return app != null ? app.loadingError : null;
    }

    @Override
    public Object start(IApplicationContext context) throws Exception {
        log.info("JavaLens MCP Server starting...");
        instance = this;

        // Sprint 14a Stage 2: parse transport CLI flags from the application
        // argv. HTTP is the default; -transport stdio opts back to the
        // pre-Sprint-14a behaviour. Unknown flags (Eclipse -data / -clean /
        // etc.) pass through. The actual HttpTransport impl lands in Stage 3.
        String[] cliArgs = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        TransportConfig transportConfig = TransportConfig.fromArgs(cliArgs);
        log.info("Transport selection: {}", transportConfig);

        // Initialize tool registry and register tools
        toolRegistry = new ToolRegistry();
        registerTools();

        // Initialize protocol handler
        protocolHandler = new McpProtocolHandler(toolRegistry);

        log.info("Registered {} tools", toolRegistry.getToolCount());

        // Sprint 10 v1.4.0: prefer workspace.json in the JDT data dir as the
        // source of truth for what to load. The manager (javalens-manager)
        // writes this file. Fall back to the legacy JAVA_PROJECT_PATH env
        // var when workspace.json is absent (back-compat for direct manual
        // launches without the manager).
        CompletableFuture.runAsync(this::autoLoadProjects);

        // Run the main message loop (starts immediately, doesn't wait for project load)
        runMessageLoop(transportConfig);

        log.info("JavaLens MCP Server stopped");
        return IApplication.EXIT_OK;
    }

    /**
     * Sprint 10 v1.4.0: load projects from {@code workspace.json} in the
     * Eclipse {@code -data} directory if present, otherwise fall back to
     * {@code JAVA_PROJECT_PATH}. This runs asynchronously so the MCP server
     * can respond to {@code initialize} immediately while loading proceeds.
     *
     * v1.7.1 (bug #5): also check the parent of the OSGi data dir. The
     * JavaLensLauncher wrapper injects a UUID subdir into {@code -data} for
     * session isolation, so OSGi's {@code osgi.instance.area} ends up at
     * {@code <workspace>/<uuid>/} while the manager writes workspace.json at
     * {@code <workspace>/workspace.json}. Without the parent-fallback every
     * non-manager-spawned JVM (Cursor, Claude Code, etc.) saw an empty
     * project list.
     */
    private void autoLoadProjects() {
        Path dataDir = resolveDataDir();
        if (dataDir != null) {
            Path workspaceJson = findWorkspaceJson(dataDir);
            if (workspaceJson != null) {
                loadFromWorkspaceJson(workspaceJson.getParent());
                return;
            }
        }
        // Fall back to single-project env-var path.
        autoLoadProjectFromEnv();
    }

    /**
     * Look for {@code workspace.json} starting at the OSGi data dir, then
     * walking up one level to handle the JavaLensLauncher session-isolation
     * subdir. Returns the path of the file if found, else {@code null}.
     *
     * <p>Public + static so unit tests in the {@code org.javalens.mcp.tests}
     * bundle can exercise the walk-up logic without booting OSGi.
     */
    public static Path findWorkspaceJson(Path dataDir) {
        if (dataDir == null) {
            return null;
        }
        Path candidate = dataDir.resolve("workspace.json");
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        Path parent = dataDir.getParent();
        if (parent != null) {
            candidate = parent.resolve("workspace.json");
            if (Files.isRegularFile(candidate)) {
                log.info("Found workspace.json in parent of OSGi data dir ({}), " +
                    "treating that as the workspace root (JavaLensLauncher session-isolation path)",
                    parent);
                return candidate;
            }
        }
        return null;
    }

    private Path resolveDataDir() {
        // Use the OSGi-defined system property rather than Platform.getInstanceLocation().getURL()
        // — the latter is a Tycho-restricted API. osgi.instance.area is set by the framework to
        // the URL of the -data dir and is the public way to read it.
        try {
            String area = System.getProperty("osgi.instance.area");
            if (area == null || area.isBlank()) return null;
            return Path.of(java.net.URI.create(area));
        } catch (Exception e) {
            log.warn("Failed to resolve Eclipse instance area: {}", e.getMessage());
            return null;
        }
    }

    private void loadFromWorkspaceJson(Path dataDir) {
        log.info("Loading workspace from {}", dataDir.resolve("workspace.json"));
        loadingState = ProjectLoadingState.LOADING;
        try {
            JdtServiceImpl service = new JdtServiceImpl();
            WorkspaceFileWatcher watcher = new WorkspaceFileWatcher(dataDir, service);
            watcher.start();  // synchronous initial load + arm watcher thread
            this.jdtService = service;
            this.workspaceWatcher = watcher;
            loadingState = ProjectLoadingState.LOADED;
            log.info("Workspace loaded; watcher armed for live updates");
        } catch (Exception e) {
            log.error("Failed to load workspace from workspace.json: {}", e.getMessage(), e);
            loadingState = ProjectLoadingState.FAILED;
            loadingError = e.getMessage();
        }
    }

    /**
     * Auto-load project from JAVA_PROJECT_PATH environment variable.
     * This runs asynchronously to allow the MCP server to respond immediately.
     * The loading state is tracked and can be queried via health_check.
     */
    private void autoLoadProjectFromEnv() {
        String projectPath = System.getenv("JAVA_PROJECT_PATH");
        if (projectPath == null || projectPath.isBlank()) {
            log.debug("JAVA_PROJECT_PATH not set, waiting for load_project call");
            // State remains NOT_LOADED
            return;
        }

        Path path = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            log.warn("JAVA_PROJECT_PATH points to non-existent path: {}", projectPath);
            loadingState = ProjectLoadingState.FAILED;
            loadingError = "JAVA_PROJECT_PATH points to non-existent path: " + projectPath;
            return;
        }

        if (!Files.isDirectory(path)) {
            log.warn("JAVA_PROJECT_PATH is not a directory: {}", projectPath);
            loadingState = ProjectLoadingState.FAILED;
            loadingError = "JAVA_PROJECT_PATH is not a directory: " + projectPath;
            return;
        }

        log.info("Auto-loading project from JAVA_PROJECT_PATH: {}", path);
        loadingState = ProjectLoadingState.LOADING;

        try {
            JdtServiceImpl service = new JdtServiceImpl();
            service.loadProject(path);
            this.jdtService = service;
            loadingState = ProjectLoadingState.LOADED;
            log.info("Project auto-loaded successfully: {} files, {} packages",
                service.getSourceFileCount(), service.getPackageCount());
        } catch (Exception e) {
            log.error("Failed to auto-load project from JAVA_PROJECT_PATH: {}", e.getMessage(), e);
            loadingState = ProjectLoadingState.FAILED;
            loadingError = e.getMessage();
        }
    }

    private void registerTools() {
        // Register HealthCheckTool with suppliers for project status, tool
        // count, loading state, and (Sprint 10) the multi-project workspace
        // summary.
        toolRegistry.register(new HealthCheckTool(
            () -> jdtService != null,
            () -> toolRegistry.getToolCount(),
            () -> loadingState,
            () -> loadingError,
            () -> jdtService
        ));

        // Register LoadProjectTool — supplies the existing service so the
        // tool can reuse it (clear-and-load on top), and installs a fresh
        // service on first call. This is what enables add_project /
        // remove_project / list_projects to operate on the same workspace.
        toolRegistry.register(new LoadProjectTool(
            () -> jdtService,
            service -> this.jdtService = service
        ));

        // Sprint 10 multi-project tools — orchestrated by javalens-manager
        // for port-grouped service consolidation. AI agents can also call
        // them, but typically don't need to: the manager pre-loads the
        // workspace's projects on service startup.
        toolRegistry.register(new AddProjectTool(() -> jdtService));
        toolRegistry.register(new RemoveProjectTool(() -> jdtService));
        toolRegistry.register(new ListProjectsTool(() -> jdtService));

        // Batch 1: Core Navigation Tools
        toolRegistry.register(new SearchSymbolsTool(() -> jdtService));
        toolRegistry.register(new GoToDefinitionTool(() -> jdtService));
        toolRegistry.register(new FindReferencesTool(() -> jdtService));
        toolRegistry.register(new FindImplementationsTool(() -> jdtService));

        // Batch 2: Type Hierarchy & Document Symbols
        toolRegistry.register(new GetTypeHierarchyTool(() -> jdtService));
        toolRegistry.register(new GetDocumentSymbolsTool(() -> jdtService));
        toolRegistry.register(new GetTypeMembersTool(() -> jdtService));
        toolRegistry.register(new GetClasspathInfoTool(() -> jdtService));

        // Batch 3: Project Structure & Position Info
        toolRegistry.register(new GetProjectStructureTool(() -> jdtService));
        toolRegistry.register(new GetSymbolInfoTool(() -> jdtService));
        toolRegistry.register(new GetTypeAtPositionTool(() -> jdtService));
        toolRegistry.register(new GetMethodAtPositionTool(() -> jdtService));
        toolRegistry.register(new GetFieldAtPositionTool(() -> jdtService));
        toolRegistry.register(new GetHoverInfoTool(() -> jdtService));

        // Batch 4: Javadoc & Method Analysis
        toolRegistry.register(new GetJavadocTool(() -> jdtService));
        toolRegistry.register(new GetSignatureHelpTool(() -> jdtService));
        toolRegistry.register(new GetEnclosingElementTool(() -> jdtService));
        toolRegistry.register(new GetSuperMethodTool(() -> jdtService));

        // Batch 5: Diagnostics & Call Hierarchy
        toolRegistry.register(new GetDiagnosticsTool(() -> jdtService));
        toolRegistry.register(new ValidateSyntaxTool(() -> jdtService));
        toolRegistry.register(new GetCallHierarchyIncomingTool(() -> jdtService));
        toolRegistry.register(new GetCallHierarchyOutgoingTool(() -> jdtService));

        // Analysis tools
        toolRegistry.register(new FindFieldWritesTool(() -> jdtService));
        toolRegistry.register(new FindTestsTool(() -> jdtService));

        // Refactoring tools
        toolRegistry.register(new RenameSymbolTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new OrganizeImportsTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new ExtractVariableTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new ExtractMethodTool(() -> jdtService, refactoringChangeCache));

        // Fine-grained reference search (JDT-unique capabilities).
        // Sprint 11 Phase D: 13 narrow find_* tools collapsed to 2 parametric ones.
        // - find_pattern_usages (annotation, instantiation, type_argument, cast, instanceof)
        // - find_quality_issue  (naming, bugs, unused, large_classes, circular_deps,
        //                        reflection, throws, catches)
        // The narrow tool classes remain in the package as the analysis
        // implementations the parametric tools delegate to; they're no
        // longer registered as user-facing MCP tools.
        toolRegistry.register(new FindPatternUsagesTool(() -> jdtService));
        toolRegistry.register(new FindQualityIssueTool(() -> jdtService));
        toolRegistry.register(new FindMethodReferencesTool(() -> jdtService));

        // Compound analysis tools
        toolRegistry.register(new AnalyzeFileTool(() -> jdtService));
        toolRegistry.register(new AnalyzeTypeTool(() -> jdtService));
        toolRegistry.register(new AnalyzeMethodTool(() -> jdtService));
        toolRegistry.register(new GetTypeUsageSummaryTool(() -> jdtService));

        // Advanced refactoring tools
        toolRegistry.register(new ExtractConstantTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new InlineVariableTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new InlineMethodTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new ChangeMethodSignatureTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new ExtractInterfaceTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new ConvertAnonymousToLambdaTool(() -> jdtService, refactoringChangeCache));

        // Sprint 11 Phase E (v1.5.1): JDT-LTK structural refactoring tools.
        toolRegistry.register(new MoveClassTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new MovePackageTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new PullUpTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new PushDownTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new EncapsulateFieldTool(() -> jdtService, refactoringChangeCache));

        // Sprint 12 (v1.6.0): Ring 1 workspace verification tools.
        toolRegistry.register(new CompileWorkspaceTool(() -> jdtService));
        toolRegistry.register(new RunTestsTool(() -> jdtService));

        // Sprint 14 Phase B.1 (v1.8.0): consolidated lifecycle tool.
        toolRegistry.register(new RefreshWorkspaceTool(() -> jdtService));

        // Sprint 14 Phase B.3 (v1.8.0): structural clone detector.
        toolRegistry.register(new FindDuplicateCodeTool(() -> jdtService));

        // Sprint 15 (v1.10.0): parametric modernization sweeps.
        toolRegistry.register(new FindModernizationTool(() -> jdtService));

        // Sprint 15 (v1.10.0): parametric clean-up catalog (upstream v1.4.2 harvest).
        toolRegistry.register(new ApplyCleanupTool(() -> jdtService, refactoringChangeCache));

        // Sprint 15a (v1.11.0): naming + Javadoc knowledge tools.
        toolRegistry.register(new AnalyzeJavadocsTool(() -> jdtService));
        toolRegistry.register(new AnalyzeNamingTool(() -> jdtService));

        // Sprint 13 (v1.7.0): Ring 2 code generation tools.
        toolRegistry.register(new GenerateConstructorTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new GenerateGettersSettersTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new GenerateEqualsHashCodeTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new GenerateToStringTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new OverrideMethodsTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new GenerateTestSkeletonTool(() -> jdtService, refactoringChangeCache));

        // Sprint 13 (v1.7.0): Ring 3 build/dep management (Maven-only).
        toolRegistry.register(new AddDependencyTool(() -> jdtService));
        toolRegistry.register(new UpdateDependencyTool(() -> jdtService));
        toolRegistry.register(new FindUnusedDependenciesTool(() -> jdtService));

        // Sprint 13 (v1.7.0): Ring 4 formatter / workflow polish.
        toolRegistry.register(new FormatTool(() -> jdtService));
        toolRegistry.register(new OptimizeImportsWorkspaceTool(() -> jdtService));

        // Quick fix tools
        toolRegistry.register(new SuggestImportsTool(() -> jdtService));
        toolRegistry.register(new GetQuickFixesTool(() -> jdtService));
        toolRegistry.register(new ApplyQuickFixTool(() -> jdtService));

        // Metrics tools
        toolRegistry.register(new GetComplexityMetricsTool(() -> jdtService));
        toolRegistry.register(new GetDependencyGraphTool(() -> jdtService));

        // Advanced analysis tools
        toolRegistry.register(new AnalyzeChangeImpactTool(() -> jdtService));
        toolRegistry.register(new AnalyzeControlFlowTool(() -> jdtService));
        toolRegistry.register(new AnalyzeDataFlowTool(() -> jdtService));
        toolRegistry.register(new GetDiRegistrationsTool(() -> jdtService));
        // Sprint 11 Phase D: FindCircularDependencies / FindReflectionUsage /
        // FindLargeClasses / FindNamingViolations / FindUnusedCode /
        // FindPossibleBugs registrations dropped — exposed via
        // find_quality_issue(kind=...) above.

        // Sprint 14b: refactoring apply/undo primitives. Staged changes and
        // undo handles live in refactoringChangeCache (1 h TTL, LRU-capped).
        toolRegistry.register(new ApplyRefactoringTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new UndoRefactoringTool(() -> jdtService, refactoringChangeCache));
        toolRegistry.register(new InspectRefactoringTool(() -> jdtService, refactoringChangeCache));
        // Sprint 14b: composite closing the find_duplicate_code loop.
        toolRegistry.register(new ReplaceDuplicatesTool(() -> jdtService, refactoringChangeCache));
    }

    private void runMessageLoop(TransportConfig config) {
        // Sprint 14a Stage 3: transport.run(handler) drives the I/O loop
        // internally. Stdio loops on its read/write pair; HTTP runs the
        // JDK HttpServer until close() signals shutdown. activeTransport
        // exposes the running transport so JavaLensApplication.stop() can
        // unblock it on OSGi shutdown.
        try (Transport transport = openTransport(config)) {
            this.activeTransport = transport;
            transport.run(protocolHandler::processMessage);
        } catch (Exception e) {
            log.error("Error in message loop", e);
        } finally {
            this.activeTransport = null;
        }
    }

    /**
     * Sprint 14a Stage 2: construct the Transport per the parsed CLI config.
     * STDIO wraps System.in/out (current behaviour, opt-in only). HTTP
     * resolves the token (generate if absent) and hands off to HttpTransport
     * which throws until Stage 3 implements it.
     */
    private Transport openTransport(TransportConfig config) {
        if (config.getKind() == TransportConfig.Kind.STDIO) {
            log.info("Transport: stdio (opt-in via -transport stdio)");
            return new StdioTransport(System.in, System.out);
        }
        String token = config.getToken() != null
            ? config.getToken()
            : TokenGenerator.generate();
        log.info("Transport: http (default) — port={}, bind={}",
            config.getPort(), config.getBindAddress());
        return new HttpTransport(config.getPort(), config.getBindAddress(), token);
    }

    @Override
    public void stop() {
        log.info("Stop requested");
        Transport t = activeTransport;
        if (t != null) {
            t.close();
        }
        WorkspaceFileWatcher watcher = workspaceWatcher;
        if (watcher != null) {
            watcher.close();
            workspaceWatcher = null;
        }
    }
}
