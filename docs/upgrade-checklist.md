# Eclipse target-platform upgrade checklist

This document lists every version-sensitive piece of code in the fork. Run through it whenever you bump the Eclipse target platform in [`org.javalens.target/org.javalens.target.target`](../org.javalens.target/org.javalens.target.target). Internal-API breakage shows up at compile time; behavior changes in JDT public API show up as test failures.

## Embedded jars (Bundle-ClassPath)

- `org.javalens.core/lib/gradle-tooling-api-8.10.jar` — pinned via `Bundle-ClassPath` in [org.javalens.core/META-INF/MANIFEST.MF](../org.javalens.core/META-INF/MANIFEST.MF) and `bin.includes` / `jars.extra.classpath` in [build.properties](../org.javalens.core/build.properties).
  - **Bumping**: drop in a newer `gradle-tooling-api-x.y.jar`, update both files, run `mvn -pl org.javalens.core.tests verify -Dtest=ProjectImporterGradleToolingTest`. The Tooling API can drive any Gradle ≥ the version of the API jar; first test run downloads the matching distribution into `~/.gradle/caches/dists`.
  - **Compatibility**: `EclipseProject` model surface has been stable across Gradle 5–9. If the test fails on a newer API version, check `EclipseSourceDirectory.getDirectory()` and `EclipseExternalDependency.getFile()` haven't been deprecated.

## Internal JDT API (Sprint 11 Phase E refactorings)

[AbstractRefactoringTool](../org.javalens.mcp/src/org/javalens/mcp/tools/AbstractRefactoringTool.java) and the five Phase E tools depend on `org.eclipse.jdt.internal.corext.*` classes. Eclipse marks these `x-internal:=true` but their de-facto stability is high — the IDE's own refactoring UI is built on them.

### Classes touched

| File                                                                                             | Internal classes                                                                                                                                              |
| ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [PullUpTool](../org.javalens.mcp/src/org/javalens/mcp/tools/PullUpTool.java)                     | `org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings`, `org.eclipse.jdt.internal.corext.refactoring.structure.PullUpRefactoringProcessor` |
| [PushDownTool](../org.javalens.mcp/src/org/javalens/mcp/tools/PushDownTool.java)                 | `org.eclipse.jdt.internal.corext.refactoring.structure.PushDownRefactoringProcessor`                                                                          |
| [EncapsulateFieldTool](../org.javalens.mcp/src/org/javalens/mcp/tools/EncapsulateFieldTool.java) | `org.eclipse.jdt.internal.corext.refactoring.sef.SelfEncapsulateFieldRefactoring`                                                                             |

`MoveClassTool` and `MovePackageTool` use only public descriptor API (`MoveDescriptor` and `RenameJavaElementDescriptor`).

### What to verify after a target-platform bump

1. Build `org.javalens.mcp` — internal-API renames / deletions fail at compile time. Fix mechanically by tracking the renamed/moved class.
2. Run `mvn -pl org.javalens.mcp.tests verify -Dtest='*RefactoringTool*'`. All happy/validation/conflict tests must stay green. One happy-path (`EncapsulateFieldToolTest.happy_encapsulatePublicField`) is `@Disabled` pending an upstream JDT fix — see "JDT bug" note below.
3. Re-check `Require-Bundle: org.eclipse.jdt.core.manipulation` in [org.javalens.mcp/META-INF/MANIFEST.MF](../org.javalens.mcp/META-INF/MANIFEST.MF) — Eclipse occasionally splits or merges plugin bundles between releases. The dependency may need to point at a different bundle or split into two.

### Resolved in v1.5.2 — JDT-UI preference defaults + cache install + change-validation

Three JDT-internal initialisation steps that Eclipse IDE's `org.eclipse.jdt.ui` plugin activator does on startup were missing in our headless RCP runtime, causing the four happy-path refactoring tests to NPE. [`AbstractRefactoringTool#initializeJdtManipulation`](../org.javalens.mcp/src/org/javalens/mcp/tools/AbstractRefactoringTool.java) now does them itself, lazily on first refactoring call:

1. `JavaManipulation.setPreferenceNodeId("org.eclipse.jdt.ui")` plus default-scope writes for `importorder` / `ondemandthreshold` / `staticondemandthreshold` so `JavaManipulation.getPreference(...)` (used by `CodeStyleConfiguration.configureImportRewrite`) returns non-null.
2. `JavaManipulation.setCodeTemplateStore(new TemplateStoreCore(...))` so `ProjectTemplateStore.fInstanceStore` is non-null when JDT looks up code templates.
3. `JavaManipulationPlugin.getDefault().getMembersOrderPreferenceCacheCommon().install()` so the cache singleton's `fPreferences` field is hydrated before any refactoring touches it.

In addition, `runRefactoring` now calls `change.initializeValidationData(monitor)` between `createChange` and `PerformChangeOperation`. Eclipse's refactoring wizard infrastructure does this implicitly via `CreateChangeOperation`; the headless path doesn't, so without it `TextFileChange.isValid()` throws "TextFileChange has not been initialialized".

Three of the four previously-disabled happy-path tests (`move_class`, `pull_up`, `push_down`) plus the new cross-bundle `pullUp_acrossOsgiBundles` integration test now pass.

### Sprint 12 (v1.6.0) — `compile_workspace` + `run_tests`

**`compile_workspace`** uses public Eclipse APIs only: `IJavaProject.build`, `IResource.refreshLocal`, `IResource.findMarkers(IMarker.PROBLEM, ...)`. No new target-platform deps. Stable across Eclipse releases.

**`run_tests`** drives JDT-LTK's JUnit launching machinery headlessly. Three new target-platform bundles (NOT just Import-Package — these are full bundles with declarative-services / extensions):

- `org.eclipse.jdt.junit.core` — public test-discovery + launch-config types + `TestRunListener` API.
- `org.eclipse.jdt.junit.runtime` — JUnit 4 runner shim, runs in the forked test JVM.
- `org.eclipse.jdt.junit5.runtime` — Jupiter runner shim, runs in the forked test JVM.

After a target-platform bump, verify these IUs still resolve from the new release's repository and that bundle `Export-Package` still includes `org.eclipse.jdt.junit.launcher` (we don't import it, but the launch type id `org.eclipse.jdt.junit.launchconfig` and the attribute keys it consumes are owned by that package). Eclipse occasionally splits or merges `jdt.junit.*` bundles between releases.

[`JUnitLaunchHelper`](../org.javalens.mcp/src/org/javalens/mcp/tools/junit/JUnitLaunchHelper.java) deliberately uses **inlined string values** for the launch-config attribute keys (`org.eclipse.jdt.junit.TEST_KIND`, `org.eclipse.jdt.junit.TESTNAME`, `org.eclipse.jdt.junit.CONTAINER`, plus the `org.eclipse.jdt.launching.PROJECT_ATTR` / `MAIN_TYPE` / `VM_ARGUMENTS` keys) instead of importing `JUnitLaunchConfigurationConstants` / `IJavaLaunchConfigurationConstants`. The values are persisted in user-saved `.launch` configuration XML so they're forever-stable; inlining sidesteps the awkward "is the constants class in the bundle's exported public-API surface?" question across Eclipse releases.

### Known limitation — `run_tests` happy-path tests `@Disabled` (v1.6.0)

The three happy-path tests in `RunTestsToolTest` (`happy_methodScope`, `happy_classScope`, `happy_packageScope`) are `@Disabled` for v1.6.0. Tycho-surefire's headless test runtime doesn't compile our sample-project fixtures — the forked test JVM needs the fixture's classes on disk, and Tycho's test stage doesn't run javac on `test-resources/sample-projects/.../src/test/java`.

Production usage works (manager → real workspace → real test classpath). Validation tests cover the input layer. Full happy-path coverage lands in **v1.6.1** along with the cross-bundle `compile_workspace` integration test.

### Sprint 13 (v1.7.0) — Ring 2/3/4 tools

**Decision: codegen via `ASTRewrite`, not `org.eclipse.jdt.ui` internal ops.** The Sprint 13 plan initially named `GenerateConstructorOperation` / `GenerateHashCodeEqualsOperation` / `GenerateToStringOperation` / `OverrideMethodsOperation` / `GetterSetterUtil`. All five live in `org.eclipse.jdt.ui` — the GUI bundle, not on our target platform. Sprint 11's LTK refactorings work around this via `AbstractRefactoringTool`'s preferences-seed shim (`initializeJdtManipulation`), but the codegen ops aren't reachable that way; they're transitively bound to JDT-UI's `JavaPlugin` activator. Resolution: build source via `ASTRewrite` directly and the JDT-DOM `AST` factory. More boilerplate per tool (~50 extra lines on average) but no new bundle dep and full headless reachability. See [`tools/codegen/`](../org.javalens.mcp/src/org/javalens/mcp/tools/codegen/).

**Decision: dep tools via `javax.xml`, not M2E API.** The Sprint 13 plan named `IMaven.readModel` / `IMavenProjectRegistry.refresh`. M2E classes are reachable on the target (the bundle is shipped) but not always **active** in headless test runtimes — Tycho-surefire doesn't auto-import projects as M2E projects. Sprint 13 ships the dep tools with `javax.xml.parsers.DocumentBuilder` for read-only inspection plus text-level mutation for in-place edits — works in any Tycho runtime. Richer M2E classpath inspection is a v1.8.x enhancement.

**Cut line — Gradle / Buildship.** Sprint 13 ships **Maven-only**. The dep tools detect non-Maven projects and return `INVALID_PARAMETER` with a clear message rather than guessing. Buildship target addition + Gradle path land as v1.8.x.

**Known limitation — `generate_test_skeleton` auto-detect path `@Disabled`.** Same fixture-build gap that has Sprint 12's `run_tests` happy-paths `@Disabled`: the simple-maven fixture's external Maven deps don't resolve onto JDT's classpath in Tycho-surefire's headless runtime, so `FrameworkDetection.detect("auto", project)` finds nothing. Production usage detects correctly. Tests pass an explicit `framework="junit5"` argument to sidestep. Re-enable once the v1.6.1+ fixture-build pipeline lands.

**Heuristic limitation — `find_unused_dependencies`.** Match logic checks (a) `groupId` as a prefix of any source import, or (b) `artifactId` (with hyphens replaced by dots) as a substring of any import. False positives are possible. v1.8.x will use M2E's resolved JAR contents for precise inspection.

**Subtle correctness bug — `optimize_imports_workspace` AST visit scope.** The naive implementation (visit the entire `CompilationUnit`) treats the import declaration's own qualified name as a use of the imported type, marking every import as "used" and removing nothing. Fix: visit only the type declarations + package declaration, never the import statements themselves. See [`OptimizeImportsWorkspaceTool.collectReferencedTypes`](../org.javalens.mcp/src/org/javalens/mcp/tools/workflow/OptimizeImportsWorkspaceTool.java).

### Sprint 14 (v1.8.x) follow-up backlog

Items deferred from Sprint 13 plus IntelliJ-parity tools the agent is missing. Surfaced here so the next sprint's plan starts from a real list.

**Gradle path for the Ring 3 dep tools.** Sprint 13 ships `add_dependency` / `update_dependency` / `find_unused_dependencies` Maven-only — they detect non-Maven projects and return `INVALID_PARAMETER`. Adding Gradle:

- Target platform: `org.eclipse.buildship.core` is **not** on our target today; add it via the existing Eclipse update site (Buildship ships from the same `download.eclipse.org` updates path as JDT). Bump `sequenceNumber` in [`org.javalens.target/org.javalens.target.target`](../org.javalens.target/org.javalens.target.target).
- `add_dependency`: append a line to the `dependencies { ... }` block in `build.gradle` / `build.gradle.kts`. Buildship's project model is read-only; mutate the file textually (preserve user formatting, same approach as the Maven path) and trigger a Gradle refresh via `org.eclipse.buildship.core.workspace.GradleBuild.synchronize`.
- `update_dependency`: regex-replace the version in the matched dep line. Single-quoted, double-quoted, and Kotlin-DSL forms all need handling.
- `find_unused_dependencies`: parse declared deps from `build.gradle` (regex over `(implementation|api|compileOnly|...)\s+['"]g:a:v['"]`); rest of the heuristic is identical to the Maven path. Optional richer mode: use Buildship's resolved-classpath model.
- Detection precedence in all three tools: `pom.xml` first (Maven wins on hybrid projects), `build.gradle*` second.

**Richer `find_unused_dependencies` via M2E classpath inspection.** The v1.7.0 heuristic (`groupId` prefix match + `artifactId`-as-dotted-suffix) has known false positives for deps whose package names don't follow the coordinate convention (e.g. some Spring-related artifacts). For a precise read, walk the M2E-resolved classpath: each declared dep has a resolved JAR; index its actual provided packages via `JarFile`/`ZipFile` walk and `package-info.class` / class-prefix extraction; cross-reference against import statements collected from the source tree. Yields zero false positives at the cost of M2E being active in the runtime — defer the check to projects with M2E auto-import on.

**Duplicate code detection (`find_duplicate_code`).** IntelliJ surfaces duplicate code via structural AST matching (the *Locate Duplicates* inspection); we don't have an equivalent today. Two reasonable implementation paths:

- **Method-granularity** (cheap, ships first): hash each method body's normalized AST (rename locals to `$0`/`$1`/…, drop literals, drop comments) and group methods with matching hashes. Useful for "these N methods are 90% identical — extract a shared one".
- **Token-stream** (PMD CPD-style): tokenize each compilation unit, find runs of N+ matching tokens across files. Catches statement-level duplication that method-AST hashing misses but at higher cost.

Start with the method-granularity tool; promote to token-stream if agent demand warrants. Result shape: `{operation, duplicates: [{members: [{filePath, line, methodName}, ...], similarity: 0.0–1.0, tokens: int}]}`. Read-only.

**Process-death → `Failed` phase.** Manager-side: today's polling flips a dead workspace to `Stopped`, not `Failed`. The Sprint 12 plan promised the tray icon flips to 🔴 within ~5s of external kill — that path needs the polling to recognise unexpected exits (non-zero exit code, no graceful-shutdown signal seen) and set `Failed` instead. Tracked here because the fork's tray menu shows the result of that aggregation.

**FQN entry point for the whole `find_*` family.** *The single most-repeated ask across every EXECSIM-Java session* (Sprint 1, 6b, 3, 4 — see `~/CursorProjects/EXECSIM-Java/docs/mcp_feedback.md`). `find_references` / `find_implementations` / `find_field_writes` all require a `(filePath, line, column)` triple that must resolve *in the project being searched* — so cross-project consumer mapping ("who in strategies-orb uses `com.orb.strategy.inference.OrbInference`?") is impossible without a `Bash grep` fallback, which becomes the fast path. Add an FQN/symbol overload: `find_references(symbol="com.orb.strategy.X", scope="workspace")` etc., no position needed. The workspace already has every project's index loaded — this is O(n) over symbols, not a re-parse. Closes the coordinate-bisection cost for interactive refactors. The schema-honesty + graceful-degradation half of this is filed as bug #12; this is the capability half.

**`refresh_workspace` / `reindex` — consolidated spec.** Repeatedly asked (Sprint 3, 3.1, 4) and proposed independently for v1.7.2. The EXECSIM feedback sharpens the requirements — it must do *all three*, not just symbol-index refresh:
1. **Refresh from disk** — pick up files created/edited via `Write`/`Edit` (the recurring "index-stale-after-Write": new package's classes invisible to `search_symbols`/`analyze_type` until reload). Sidesteps bug #6's broken watcher.
2. **Invalidate JDT's incremental compile cache** — not just the symbol index. Without this, bug #8 (`compile_workspace` false-pass on record/signature shape change) stays open: JDT reuses class files compiled against the old signature.
3. **Preserve workspace/`projectKey` state** — must NOT drop projects or rotate keys (today `remove_project`+`add_project` is the only knob and it resets everything, invalidating the caller's `projectKey` → bug #11). Per-project scope: `refresh_workspace(projectKey?)`.
Effectively this one tool also closes bug #8 and is the manual override for bug #6. Returns post-rebuild diagnostics, `compile_workspace` shape.

**`copy_class` + `wrap_class` — strangler-fig cross-project migration toolset.** From EXECSIM Sprint 1 (30+ class cross-project migration). `move_class` is destructive and within-project (bug #10); real migrations need the duplicate-wrap-redirect-cap protocol:
- `copy_class(from=FQN, to=FQN)` — duplicate into the target package with the normal package-decl rewrite on the *copy*, original untouched and still referenced. Removes the `cp + sed -i package` bash step from every atomic-protocol iteration (~30 round-trips saved in one migration).
- `wrap_class(wrapperFqn, targetFqn, deprecate=true)` — rewrite the original into a thin delegate (every public method → `return target.method(args)`, copy constructor signatures, add `@Deprecated`). Nice-to-have; wrapper bodies are simple enough that a script handles them, so lower priority than `copy_class`.
These plus the bug #10 `move_class` fixes form a coherent "Fowler StranglerFig / Branch-by-Abstraction support" arc.

**`pre_edit_impact(filePath, line, col)` — proactive impact summary.** Asked Sprint 6b and Sprint 4 (the `change_method_signature`/`analyze_change_impact` skips that cost a parity-run iteration each). A cheap 5-line summary returned *before* an edit — "N callers in M files, K of them test files, complexity X, recently-changed by Y" — pushes impact-awareness into the default workflow without the caller remembering to invoke `analyze_change_impact` separately. Friction-reduction, not new capability; the analysis already exists, this is a packaged entry point.

**Raise `find_references` truncation cap.** Currently hard-capped at 100; a 100-reference class returns exactly 100 with no signal there are more. Make it a parameter (default 100, caller can raise) or return a `truncated: true, total: N` field.

**HTTP/SSE transport (strengthens v1.7.1 #5 Option B).** EXECSIM Session 2 documented that `stdio` MCP spawning is *completely broken* inside sandboxed agents (Antigravity `nsjail` on Ubuntu 24.04 / aarch64 GB10) AND that a containerized agent targeting the same `-data` dir as a running Claude hangs forever on the Eclipse JDT workspace lock. HTTP/SSE on the host (manager runs one JVM per workspace, clients connect by URL) bypasses both the broken local-spawn and the lock contention. This was the deferred Option B for the multi-tenant problem; the sandbox evidence makes it the durable fix, not just an optimisation.

### Known limitation — `EncapsulateField` happy-path (JDT 2024-09 bug)

`SelfEncapsulateFieldRefactoring.createSetterMethod` has a bug in its fallback path: when `CodeGeneration.getSetterMethodBodyContent()` returns null (because no `org.eclipse.jdt.ui.text.codetemplates.setterbody` template is registered), it creates a bare `Assignment` AST node and calls `block.statements().add(ass)`. `Block.statements()` expects `Statement` instances, so this fails with `class Assignment is not an instance of class Statement`.

To make this work in headless mode we'd need to recreate Eclipse JDT-UI's full code-template machinery: `JavaContextType` (subclass of `CodeTemplateContextType` registering `${field}` / `${enclosing_method}` / etc. variables) plus a populated `ContributionContextTypeRegistry` set via `JavaManipulation.setCodeTemplateContextRegistry(...)`. That's deeper into JDT-UI internals than is reasonable to maintain across target-platform bumps.

The fix belongs upstream — `createSetterMethod` should wrap the fallback `Assignment` in an `ExpressionStatement` before adding it to the block. Until then, `EncapsulateFieldToolTest.happy_encapsulatePublicField` stays `@Disabled` with an explanatory message; validation and conflict paths still cover the tool. The other four happy-path tests pass.

## Agent feedback — what actually moves the needle

Field notes from real agent sessions, dated. Goal: prioritise the tools that change agent behaviour for the better; cut or hide the rest. Not aspirational — what was used vs what wasn't.

### 2026-05-05 — strategies_orb Sprint 6a Wave 1 (Slot encapsulation + S-PhaseC/FixB/Gap3)

Refactor across one bundle, ~50 call sites, ~30 new tests, three small architectural changes. Agent: Claude (Opus 4.7), workspace `jl-jats-orb-ws` loaded.

**Used and earned its keep:**

- **`compile_workspace --projectKey ... --minSeverity ERROR`** — invoked 4×, sub-second clean reads. Replaces a slow `mvn clean test-compile` round-trip (≈30 s) at every checkpoint. Caught one stale-Maven-class divergence: Maven said "nothing to compile" while my edits had broken `Slot.java`; `compile_workspace` returned the real errors instantly. **This is the killer tool. Keep it fast, keep it reliable.**
- **`find_references` / `find_implementations`** — used by Phase A research subagents. Produced a clean rewrite-surface inventory that caught 3 sites my plan had missed (`OrbJatsStrategyParityRunner:460`, `NettingGuardTest:255/258`). For any field/method whose name collides with non-Java tokens or appears in comments/strings, `find_references` beats grep on signal-to-noise.

**Not used, despite plan calling for them:**

- **`encapsulate_field`** — skipped. The refactor needed a *custom* API shape (`addEquityUsd(delta)` instead of an auto-generated `setEquityUsd(value)`, plus a symmetry rewrite on `setTpOrder`/`clearTpOrder`). The MCP tool would have produced mechanical accessors I'd then hand-edit. `Edit` + a one-liner `sed` for the 50+ call sites was shorter.
- **`rename_symbol`** — skipped for the same reason (rename was bundled into a shape change). User notes that when the task IS "rename X everywhere", `rename_symbol` does work and is preferred over sed. Confirmed. **Right tool, wrong task in this session.**
- **`extract_method` / `extract_variable` / `move_class` / `pull_up` / `push_down`** — never needed. Sprint shape was localised.

**Friction observed:**

- **`ToolSearch` deferred-tool dance** is real friction. To call any tool not in the always-loaded set, the agent must `ToolSearch query="select:<name>"` first to load the schema, then call. For tools the agent might use 0-or-1 times per session, this raises the bar to "is this clearly worth the round-trip" and most aren't. Agents (rationally) fall back to `Bash` + `grep`/`sed` for one-shot questions. **The long-tail tools are effectively invisible unless the agent already knows their name.** Two possible fixes:
  - Default-load the high-value tools (`compile_workspace`, `get_diagnostics`, `find_references`, `find_implementations`, `rename_symbol`) so agents don't pay the round-trip.
  - Or add a `list_tools` / `tool_recommendations` entry-point that surfaces "for *this* kind of task, consider these tools" so agents can discover without already knowing.
- **Refactoring tools are go/no-go with no preview.** When an agent considers `encapsulate_field`, it has no way to know whether the resulting accessor names + visibility match what's wanted without running the refactor and then either accepting or backing out. A **dry-run mode that returns the proposed diff** would let agents pick "MCP refactor vs manual" with confidence. Today the safe choice is manual.

**Suggestions ranked by likely impact on agent precision:**

1. **Default-load the killer tools.** `compile_workspace` + `get_diagnostics` + `find_references` + `find_implementations` + `rename_symbol`. Removing the `ToolSearch` round-trip on these alone would change agent behaviour. Long-tail tools can stay deferred.
2. **Dry-run mode for every refactoring tool.** Return the unified diff that *would* be applied. Agent decides go/no-go. Avoids the "if the shape doesn't match exactly, manual is faster" failure mode.
3. **Structured `find_references` output.** Currently agents post-process the raw output with regex; if the tool returned `[{filePath, line, kind: "read"|"write"|"declaration", snippet}]` directly, it'd match how agents actually need to use it (rewrite surface inventory). Write/read classification specifically would have saved Phase A subagents a parsing pass.
4. **Headline a small set of recommended-tools.** Most of the ~80 tools in the workspace are completist. The agent has no idea which 5 are load-bearing and which 75 are belt-and-braces. A short README in the workspace surfacing "for refactor sprints, reach for X/Y/Z first" would reduce the "fall back to bash" reflex.

**What would NOT help:**

- More tools. The catalogue is already long.
- Tighter integration with niche IDE features. Agents don't use them.
- Anything that requires a multi-step setup before the first call.

### 2026-05-11 → 05-17 — EXECSIM-Java Sprints 1–4 + 6b (6 sessions, full log in that repo)

Full feedback: [`~/CursorProjects/EXECSIM-Java/docs/mcp_feedback.md`](file:///home/harald/CursorProjects/EXECSIM-Java/docs/mcp_feedback.md). Workspace: `execsim-java` + `strategies-orb` + `orb-java` (3 Maven, JDK 21, aarch64). Cumulative pattern across 6 sessions:

**Earned its keep, every session:** `compile_workspace` as the post-edit inner loop (≈1 s vs ~30 s `mvn compile`) — the single highest-value capability. `analyze_type` / `get_type_members` / `search_symbols` for the first 10–20 min of any unfamiliar-area refactor (one `analyze_type` ≈ 5 `Read`s). `find_references` for signature-change impact sizing (36 refs in one call vs 4–5 greps). `change_method_signature` when the caller remembers it exists (one call vs 9 chained Edits — Harald caught the miss once).

**Cumulative friction (now filed):** the build-status signal is wrong for shape changes (bug #8 stale incremental cache, #9 no test-source compile) — "this is more serious than the navigation-index gap; the caller advances on a broken tree". `move_class` cross-project deficiencies (#10). `projectKey` silently dropped by state mutation (#11). `find_*` family position-only, no FQN (#12 + the v1.8.x feature). Index-stale-after-`Write` → fallback to `Read`+`grep` for net-new packages.

**The recurring meta-conclusion** (verbatim, Session 3): *"The capability surface covers the actual use cases — what's missing is operational hygiene (reindex, key stability, error messages) rather than new tools. Don't add features; fix the lifecycle."* Session 4 restated: *"Capability surface is right; lifecycle is where the friction lives."* This reprioritises the v1.8.x backlog: **the `refresh_workspace`/`reindex` consolidation + the `compile_workspace` correctness fixes (#8/#9) outrank the new-tool items (`find_duplicate_code`, modernisation arc) for agent-trust impact.** The catalogue is not the bottleneck; the index/build-cache lifecycle is.

**Sandbox finding (Session 2):** `stdio` spawning is fatally broken inside Antigravity's `nsjail` on the GB10; lock contention with a running Claude is unavoidable. HTTP/SSE on the host is the only reliable path for containerized agents — promotes v1.7.1 #5 Option B from "optimisation" to "durable fix".

### Template for future sessions

Date — task — used (with count) / not used (with reason) / friction / suggestions. Keep it terse; the value is in the cumulative pattern across sessions, not any single entry.

## Tycho project-import edges

- [ProjectImporter.readPomPackaging](../org.javalens.core/src/org/javalens/core/project/ProjectImporter.java) — reads `<packaging>` directly from pom.xml. Stable across Maven versions.
- The Maven shell-out for non-Tycho projects (`mvn dependency:build-classpath -Dmdep.outputFile=...`) still depends on the Maven plugin behaving as in 3.9.x. Re-run `mvn -pl org.javalens.core.tests verify -Dtest=ProjectImporterTest` after Maven major-version bumps.

## File watcher

- [WorkspaceFileWatcher](../org.javalens.core/src/org/javalens/core/workspace/WorkspaceFileWatcher.java) uses `java.nio.file.WatchService`. JVM/Linux/macOS-stable; Windows quirk: WatchService events for renames sometimes fire as `ENTRY_DELETE` + `ENTRY_CREATE` rather than `ENTRY_MODIFY`. We already handle both.

## Workflow

When bumping the target platform:

1. Edit [`org.javalens.target/org.javalens.target.target`](../org.javalens.target/org.javalens.target.target) — update repository URLs and bump `sequenceNumber`.
2. `mvn -pl org.javalens.target install`.
3. `mvn -pl org.javalens.core install -DskipTests` — catches public-API renames in JDT/LTK.
4. `mvn -pl org.javalens.mcp install -DskipTests` — catches internal-API breakage in the Phase E tools.
5. `mvn -pl org.javalens.core.tests verify` — public-API regression coverage.
6. `mvn -pl org.javalens.mcp.tests verify` — tool-surface regression coverage.
7. Smoke-test against a real project (e.g. JATS) through the manager. Regressions in JDT search semantics or Tycho project-import don't show up in unit tests.
