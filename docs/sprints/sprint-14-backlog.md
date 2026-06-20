# Fork Sprint 14 Backlog — v1.8.0

> **Status: draft, written 2026-06-04 (post manager v0.14.0 + v0.14.1 ship).** First fork sprint after the v1.7.2 drop. **Theme: lifecycle hygiene — agent-trust restoration.**

> **Predecessors:**
> - Fork [Sprint 13 (v1.7.0)](sprint-13-backlog.md) — Ring 2/3/4 tools (73 tools per workspace).
> - Fork v1.7.1 (4465e09, 2026-05-11) — 5 bugs fixed in patch release; baseline for v1.8.0 work.
> - Fork v1.7.2 — **DROPPED 2026-06-04** (fork is independent of manager release pairing).
> - Manager Sprint 14 (v0.14.0) + v0.14.1 hotfix — both shipped 2026-06-04, independent of this sprint.

> **Numbering note:** this is **the FORK's Sprint 14**, targeting **fork v1.8.0**. Not the same as the manager's Sprint 14 which already shipped v0.14.0. Each repo numbers its own sprints from its own history (fork: ...12 → 13 → **14** → 15...; manager: ...12 → 13 → 14 → ...).

## Goal

Close the seven open fork bugs (`bugs.md` #6 – #12, plus new #13 from 2026-06-03 ORB feedback) and ship the **consolidated `refresh_workspace` tool** + the **FQN-based `find_*` overload** + the **Gradle path for Ring 3 dep tools**. Plus a fork-side doc-scrub mirroring the manager v0.14.1 scrub.

**Why this theme, not "new tools":**

The cumulative meta-conclusion across six 2026-05 EXECSIM-Java sessions (recorded verbatim in `docs/upgrade-checklist.md` agent-feedback section):

> *"The capability surface covers the actual use cases — what's missing is operational hygiene (reindex, key stability, error messages) rather than new tools. Don't add features; fix the lifecycle."*

The 2026-06-03 ORB feedback session [`feedback_javalens_usage_learnings`](file:///home/harald/.claude/projects/-home-harald-CursorProjects-ORB/memory/feedback_javalens_usage_learnings.md) reinforces this: the workhorses (`compile_workspace` + `get_diagnostics` as the post-edit verify gate, `search_symbols`/`find_references` as the navigation spine) are reliable; the bugs that erode trust are in the lifecycle layer (`run_tests` Maven + PDE failures, `move_class` cross-file `@link` rewrites, `rename_symbol` missing constructors, `compile_workspace` false-pass on signature changes, index-stale-after-`Write`).

The catalogue is not the bottleneck. Fix the index/build-cache/error-code lifecycle, and the existing tools start delivering full value.

**End-of-sprint outcome:**

- Per-workspace tool count: **73 → 75** (refresh_workspace + FQN overload tool wrapper; the Gradle path extends the existing Ring 3 tools without adding tool-count).
- Seven OPEN bugs → `FIXED in v1.8.0` (#6, #7, #8, #9, #10, #11, #12), plus new #13.
- Agent-trust restoration: `compile_workspace`'s green is authoritative; `find_*` works without `(filePath,line,column)` for workspace-scope queries; `projectKey` failures distinguish dropped vs. typo; `rename_symbol` covers the constructor.
- Fork tagged `v1.8.0`. Manager's release-poller picks it up automatically (no manager-side change needed).

## Repos touched

- **`javalens-mcp` (fork)** — all stages. Phase A bugs are spread across `org.javalens.core` (workspace + watcher + classpath) and `org.javalens.mcp` (tool surface + launchers). Phase B (refresh_workspace + FQN overload) is `org.javalens.mcp`. Phase C (Gradle dep tools) is `org.javalens.mcp` + target platform additions in `org.javalens.target`. Phase D is docs only. Cut release `v1.8.0`.
- **`javalens-manager`** — none. Release-poller already polls `hw1964/javalens-mcp` (default repo).

## Out of scope (settled)

- **Android read-only tools** (manifest / resource / layout analysis) — Sprint 15+. See manager [`future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) Android section.
- **Fowler smell detection** (Half 2 of the Fowler arc — ~18 detection tools) — Sprint 15 or 16. See manager future-sprint-enhancements "Fowler" section.
- **Multi-step orchestration framework** (`plan_refactoring` + `apply_refactoring_plan` + rollback) — Sprint 16+. Foundation for Kerievsky / SOLID / target-form catalogs.
- **Modernisation sweeps** (find-and-batch-apply: anonymous→lambda, switch→pattern, loop→stream, Optional introduction) — Sprint 16+.
- **HTTP/SSE transport** (durable fix for sandboxed-agent stdio spawn breakage, per 2026-05-17 EXECSIM Session 2) — Sprint 15+. Big enough to warrant its own sprint.
- **Richer `find_unused_dependencies` via M2E classpath inspection** — Sprint 15+. The v1.7.0 heuristic stays as-is for v1.8.0; M2E path requires the runtime to have M2E auto-import active.
- **`copy_class` + `wrap_class`** (strangler-fig cross-project migration toolset) — Sprint 15+. Closes bug #10 from a different angle but #10 itself is fixed in v1.8.0.
- **`pre_edit_impact`** — Sprint 15+. Friction-reduction; not blocking.
- **Raising `find_references` truncation cap** to a caller-controlled parameter — could fold into Phase B if cheap; otherwise Sprint 15.

## Authorship / attribution rule

Same as Sprints 11 / 12 / 13 and the manager v0.14.0 / v0.14.1 work: **zero AI-attribution boilerplate** in commits, release notes, code, or docs produced during execution. See [`feedback_no_coauthored_trailer.md`](file:///home/harald/.claude/projects/-home-harald-CursorProjects-javalens-manager/memory/feedback_no_coauthored_trailer.md).

## Workflow rule

Same focused-tests-during-dev / full-verify-at-sprint-end pattern from Sprint 13:

- **During development of any tool**: focused per-tool test via `mvn -pl org.javalens.mcp.tests -am verify -Dtest=ToolNameTest` (~2 min).
- **Compile-only loop** when iterating: `mvn -pl org.javalens.mcp -am compile` (~30 s).
- **Full reactor `mvn clean verify`** runs **once** at end of sprint as the smoke + regression gate before tagging. Sprint exit only.
- **No** `mvn clean verify` "just to check" between phases.

Per CLAUDE.md collaboration spec: STOP at every phase checkpoint with the format-checklist summary; wait for `continue`; never silently skip/advance past a failure.

## Order of work

Phase A is the bug-fix bulk (sequence by dependency; #6 + #8 land together because both feed into refresh_workspace's design). Phase B introduces the consolidated `refresh_workspace` tool — depends on Phase A's lifecycle fixes. Phase C is independent (Gradle dep tools); can be parallelised with Phase B if multiple developers. Phase D is docs + scrub + cutover.

1. **Phase A — Lifecycle bugs** (~5 days, 7 bugs across `core` + `mcp` modules).
   - A.1 — Bug #7 (classpath dedupe). Small atomic fix.
   - A.2 — Bug #6 (`WorkspaceFileWatcher` ENTRY_CREATE + debounce).
   - A.3 — Bug #13 NEW (`rename_symbol` constructor-rename gap).
   - A.4 — Bug #10 (`move_class` cross-project + javadoc `@link` rewrites).
   - A.5 — Bug #8 (`compile_workspace` clean / incremental-cache invalidation).
   - A.6 — Bug #9 (`compile_workspace` test-source scope).
   - A.7 — Bug #11 (`PROJECT_KEY_DROPPED` distinct error code).
   - A.8 — Bug #12 schema-honesty half (`find_*` docs updated; FQN feature is Phase B).
   - A.9 — Bug #1 full fix (Maven + PDE `run_tests` launch paths; v1.7.1 shipped only the workaround dispatch).
2. **Phase B — `refresh_workspace` + FQN-based `find_*` + `find_duplicate_code`** (~5 days).
   - B.1 — `refresh_workspace(projectKey?)` consolidated tool. Ties #6 (full fix), #8 (incremental cache), and projectKey preservation into one entry point.
   - B.2 — FQN overload for `find_references`, `find_implementations`, `find_field_writes`, `find_method_references` (Phase B's flagship — "single most-repeated agent ask").
   - B.3 — `find_duplicate_code(projectKey?, minTokens?, threshold?)` — method-granularity AST-normalized-token clone detection (PMD-CPD style), workspace-scoped.
3. **Phase C — Gradle path for Ring 3 dep tools** (~3 days, parallel-safe with Phase B).
   - C.1 — Target platform: add `org.eclipse.buildship.core` + friends; bump `sequenceNumber`.
   - C.2 — `add_dependency` / `update_dependency` / `find_unused_dependencies` Gradle path.
   - C.3 — Detection precedence: `pom.xml` first, `build.gradle*` second.
4. **Phase D — Fork-side doc-scrub + cutover** (~1 day).
   - D.1 — Fork-side doc-scrub (mirrors manager v0.14.1 Stage 5; forward-only anonymize).
   - D.2 — Full reactor `mvn clean verify` (~20 min sprint-boundary gate).
   - D.3 — v1.8.0 tag + GitHub release-as-Latest. Manager's release-poller picks it up automatically.

Total ~14 days of focused work (~3 weeks calendar).

---

## Phase A — Lifecycle bugs

Each sub-phase is a single bug closure with a focused test (or test extension) verifying the fix. Per bug entry in `docs/bugs.md` has the suggested-fix shape; this section captures the additional design / sequencing decisions.

### A.1 `bugs.md` #7 — Classpath dedupe in `ProjectImporter.addDependencyEntries()`

**File:** `org.javalens.core/src/org/javalens/core/project/ProjectImporter.java`.

**Sketch:** dedupe `IClasspathEntry` by `entry.getPath().toFile().getCanonicalPath()` before `setRawClasspath()`. INFO log on first dedupe per project so the impact is observable.

**Test:** `ProjectImporterTest.addDependencyEntries_dedupesSamePathFromDirectoryAndClasspathXml` — fixture with `lib/` dir + explicit `.classpath` `<classpathentry kind="lib">` pointing at the same jar; assert resulting array length = 1.

**Smoke:** with manager running, `add_project /home/harald/CursorProjects/javalens-mcp` against the fork itself → success, no duplicate-classpath error.

### A.2 `bugs.md` #6 — `WorkspaceFileWatcher` ENTRY_CREATE + debounce

**File:** `org.javalens.core/src/org/javalens/core/workspace/WorkspaceFileWatcher.java`.

**Sketch:** register both `ENTRY_MODIFY` and `ENTRY_CREATE` (Linux atomic-rename emits CREATE on the new inode). 200 ms debounce. WARN log on reconciliation error (currently swallowed).

**Test:** `WorkspaceFileWatcherTest.atomicRenameWrite_triggersReconciliation` — fixture write via `Files.move(tmp, target, REPLACE_EXISTING)`; assert callback fires within 500 ms. Existing `*Test` cases must stay green (single-project watch path is regression-sensitive).

### A.3 `bugs.md` #13 NEW — `rename_symbol` misses constructor name on class rename

**Date observed:** 2026-06-03 (ORB session).

**Reproducer:** rename a Java class `Foo` → `Bar` via `rename_symbol`. The returned edit set rewrites the type name in declarations and references but leaves the constructor's identifier as `Foo`. Caller fell back to `Edit replace_all` on the class name as more complete.

**Suggested fix:** add a post-pass after JDT's rename returns its edits — walk the AST of every modified compilation unit, find `MethodDeclaration` nodes where `isConstructor()` AND the simple name equals the OLD class name, and emit an edit changing the simple name to the NEW class name. Idempotent if JDT already covered it.

**Test:** `RenameSymbolToolTest.classRename_alsoRenamesConstructors` — fixture with a class that has 2 constructors (no-arg + arg); rename; assert the resulting source has both constructors using the new name.

**File entries to add to `docs/bugs.md` at the top (newest first):** see Stage 0 doc work in Phase D.1 — drafted there so the scrub and the bug entries land in one commit.

### A.4 `bugs.md` #10 — `move_class` cross-project + javadoc `@link` rewrites

**File:** `org.javalens.mcp/src/org/javalens/mcp/tools/MoveClassTool.java` (uses public `MoveDescriptor`).

**Three sub-defects to close:**
1. **No physical relocation across projects.** When `targetPackage` matches a different project's source root, the file must be moved on disk. Add `targetProjectKey` param (or auto-detect from `targetPackage` against loaded projects' source roots).
2. **`modifiedFiles: []` always empty.** Walk the `Change` returned by `MoveDescriptor.createDescriptor()`; collect modified file paths and return them.
3. **Javadoc `@link` import rewrites — recently confirmed in ORB 2026-06-03 session.** JDT's `MoveDescriptor` rewrites `@link Foo` references AND adds an import for the new FQN in every file that has a javadoc reference. Strip the import-edits from the Change before applying (or filter to import-edits that REPLACE an existing import for the OLD FQN, never net-new ones).

**Test extensions to `MoveClassToolTest`:**
- `crossProjectMove_relocatesFilePhysically` — move from project A to project B; assert file moved on disk.
- `crossProjectMove_populatesModifiedFilesArray` — assert non-empty + contains the destination file + consumer files.
- `move_doesNotAddJavadocLinkImports` — fixture with javadoc `@link Foo` reference; assert moved file's import list unchanged for files where the OLD FQN wasn't imported.

### A.5 `bugs.md` #8 — `compile_workspace` clean / incremental-cache invalidation

**Files:** `org.javalens.mcp/src/org/javalens/mcp/tools/CompileWorkspaceTool.java` + a new helper in `org.javalens.core` for the clean-rebuild path.

**Sketch:** add `clean: bool` param (default `false`). When `true`: call `IProject.build(IncrementalProjectBuilder.CLEAN_BUILD, ...)` on each project in scope, then `IncrementalProjectBuilder.FULL_BUILD`. The "stale incremental cache" failure mode is JDT reusing class files compiled against an old record / public-method signature; CLEAN_BUILD drops them.

**Performance:** clean-build is ~10× slower than incremental on large workspaces. Default to `false` (current behavior) — agents opt in for "signature changed, want trustworthy green."

**Folds into Phase B:** `refresh_workspace` invokes `compile_workspace(clean=true)` internally as one of its steps.

**Test:** `CompileWorkspaceToolTest.cleanRebuild_invalidatesStaleClassFiles` — fixture where `LabelCacheKey` is a 5-field record; assert call sites compile against it; mutate to 7-field record; `compile_workspace(clean=false)` → `errorCount: 0` (the false-pass we're fixing). `compile_workspace(clean=true)` → `errorCount: 3` matching the changed call sites.

### A.6 `bugs.md` #9 — `compile_workspace` test-source scope

**File:** `org.javalens.mcp/src/org/javalens/mcp/tools/CompileWorkspaceTool.java`.

**Sketch:** add `scope: "main" | "test" | "both"` param (default `"both"`). For each project in scope, walk `IJavaProject.getRawClasspath()` for `IClasspathEntry.CPE_SOURCE` entries and filter by the entry's `isTest()` flag (set by M2E for `src/test/java`; PDE bundles use `Bundle-Header: Eclipse-BundleShape: tests`). Compile each filtered root via the same `IJavaProject.build` path.

**Backward compatibility:** default flip from `"main"`-only to `"both"` is a behavior change. Release note prominently lists it. Callers that explicitly want main-only pass `scope: "main"`.

**Test:** `CompileWorkspaceToolTest.scopeBoth_compilesTestSources` — fixture with `src/main/java` clean + `src/test/java` containing a deliberate type error; assert default call returns `errorCount: > 0` and the diagnostic points at the test file.

### A.7 `bugs.md` #11 — `PROJECT_KEY_DROPPED` distinct error code

**Files:** workspace-state types in `org.javalens.core` + every tool that takes `projectKey`.

**Sketch:** `ProjectRegistry` tracks `{ projectKey, droppedAt? }` for keys that were unloaded (by manager or other clients) vs. never-valid. Every tool's `projectKey` validation flow:
- Key never existed → `INVALID_PARAMETER` (current).
- Key existed but was unloaded → `PROJECT_KEY_DROPPED { droppedAt, reason }`, with hint: `re-acquire via list_projects`.

**Optional:** add an `epoch` token to `list_projects` output so callers can detect state shifts before a keyed call fails.

**Test:** `ProjectRegistryTest.droppedKey_returnsDistinctErrorCode` — register + drop a key; assert subsequent tool calls return `PROJECT_KEY_DROPPED`, not `INVALID_PARAMETER`.

### A.8 `bugs.md` #12 schema-honesty half — `find_*` docs updated

**Files:** schema descriptions for `FindImplementationsTool`, `FindFieldWritesTool`, `FindMethodReferencesTool`, `FindReferencesTool`.

**Sketch:** rewrite each schema description to lead with the file/coord requirement explicitly. Use the same fix-class as v1.7.1 bug #3 (`run_tests` schema rewrite). Example for `find_implementations`:

```
Find implementations of an interface or abstract method.

Inputs (position-based — the symbol must resolve at the given coords):
- filePath, line, column — zero-based; the position must point at an
  interface or abstract method declaration.
- (v1.8.0: an alternative FQN-based overload is available — see
  find_implementations_by_fqn for workspace-scope queries that don't
  need a position.)
```

**Plus `find_field_writes` graceful-degradation:** when the position resolves to a non-field, instead of refusing with `Symbol at position is not a field (found: Method)`, return nearby field candidates within the same compilation unit (up to 5, ranked by token distance from the requested position).

**Test:** schema-text snapshot tests + `FindFieldWritesToolTest.position_offByLines_returnsNearbyFieldCandidates` — fixture where coords land on a method declaration adjacent to a field; assert result is `{candidates: [...], requestedKind: "Field", foundKind: "Method"}` instead of an `INVALID_PARAMETER`.

### A.9 `bugs.md` #1 full fix — Maven + PDE `run_tests` launch paths

v1.7.1 shipped the workaround dispatch (Maven side returns `INVALID_PARAMETER` with `mvn -Dtest=...` hint). v1.8.0 ships the full launch paths.

**Maven path:**
- Detect via existing `BuildSystem` enum (`MAVEN` / `MAVEN_WITH_PDE_NATURE`).
- Use `JUnitLaunchConfigurationDelegate` directly with M2E-resolved classpath. The 2026-06-03 ORB feedback confirms M2E's classpath container resolves correctly in the fork's headless runtime via the `Require-Bundle: org.eclipse.m2e.core;resolution:=optional` shim added in Sprint 13.
- Pre-flight: skip the OSGi `Bundle.getHeaders()` lookup for non-`PluginNature` projects.

**PDE path:**
- Currently the PDE side fails framework detection even with explicit `framework=` param (per [`feedback_javalens_usage_learnings`](file:///home/harald/.claude/projects/-home-harald-CursorProjects-ORB/memory/feedback_javalens_usage_learnings.md): "Cannot find 'junit.framework.TestCase'" or "Cannot find 'org.junit.platform.commons.annotation.Testable'", sometimes the OSGi NPE).
- Root cause: PDE bundles don't have JUnit on their bundle classpath unless declared in `MANIFEST.MF`'s `Require-Bundle`. The launcher tries to instantiate the runner shim and fails on the missing class.
- Fix: for PDE projects, scan the project's `IPluginModelBase`'s required-bundle list for `org.junit` / `junit` / `org.junit.jupiter.api` to determine framework; if explicit `framework=` is passed, trust it and configure the runner shim with the workspace-level JUnit bundles instead of project-local.

**Tests:**
- `RunTestsToolMavenPathTest` — three currently-`@Disabled` happy-path tests (`happy_methodScope`, `happy_classScope`, `happy_packageScope`) flip to enabled and pass against a plain Maven fixture (typical multi-module Maven layout).
- `RunTestsToolPdePathTest` — fixture mirroring an Eclipse PDE bundle layout; explicit `framework="junit5"` succeeds; missing-junit-bundle case returns actionable error message (not the OSGi NPE).

## Phase B — `refresh_workspace` + FQN-based `find_*`

### B.1 `refresh_workspace(projectKey?)` consolidated tool

**File (new):** `org.javalens.mcp/src/org/javalens/mcp/tools/RefreshWorkspaceTool.java`.

**Sketch:** new tool that performs ALL THREE of the lifecycle steps the upgrade-checklist v1.8.x backlog identifies:
1. **Refresh from disk** — pick up files created/edited externally (via `Write`/`Edit` outside the watcher's notice). Walk every loaded project's source roots, refresh `IResource.refreshLocal(DEPTH_INFINITE)`. Sidesteps the watcher gap from #6.
2. **Invalidate JDT's incremental compile cache** — equivalent of `compile_workspace(clean=true)` from A.5. CLEAN_BUILD then FULL_BUILD on each project.
3. **Preserve `projectKey` state** — does NOT drop projects or rotate keys. Per-project optional scope: `refresh_workspace(projectKey?)` — without it, refreshes every loaded project.

**Return shape:** post-rebuild diagnostics (same shape as `compile_workspace`'s output), plus `refreshedProjects: [...]`, plus `summary: { filesRefreshed, classFilesInvalidated, errorCount, warningCount }`.

**Closes:** #6 (more completely than the watcher fix alone), #8 (the consolidated path is the one a caller invokes when they've made signature changes), addresses the lifecycle gap surfaced across every EXECSIM session.

**Test:** `RefreshWorkspaceToolTest`:
- `refresh_picksUpFileWrittenViaBash` — write a new file to a project's source root via plain `Files.write`; assert post-refresh `search_symbols(qualifiedName)` finds it.
- `refresh_invalidatesStaleClassFiles` — mirror A.5's test against the new tool.
- `refresh_preservesProjectKey` — capture `projectKey` pre + post; assert identical.
- `refresh_scopedToOneProject` — multi-project workspace; assert only the named project's data churns (other projects' `lastBuilt` timestamps unchanged).

### B.2 FQN overload for `find_*` family

**Files (extended):** `FindReferencesTool`, `FindImplementationsTool`, `FindFieldWritesTool`, `FindMethodReferencesTool` schemas + handler logic. Plus a shared helper `org.javalens.mcp.tools.fqn.FqnResolver`.

**Sketch:** add an alternative invocation path that takes `symbol: string` (an FQN like `com.foo.Bar` for a type or `com.foo.Bar#methodName(int)` for a method) and `scope: "workspace" | "project"` (`projectKey?` when scope=`project`). Internally resolves the FQN to an `IJavaElement` via `IJavaProject.findType` / `IType.getMethod` / `IField`, then runs the existing position-based search code.

**Why now (vs. later):** flagged as "the single most-repeated ask across every EXECSIM-Java session" in upgrade-checklist. Closes the cross-project consumer-mapping workflow that today falls back to `Bash grep`. Each per-tool extension is a small AST-side change — ~1 day total for all four `find_*` tools.

**Schema honesty:** the existing position-based shape stays; the FQN shape is additive. Schema description states both shapes explicitly so a caller doesn't have to guess.

**Test:** `FindReferencesByFqnTest`, etc. — fixture with class `A` in project P1 referencing class `B` in project P2; `find_references(symbol="com.example.B", scope="workspace")` returns both projects' callers without per-project coord lookups.

### B.3 `find_duplicate_code` — method-granularity AST-normalized clone detector

**File (new):** `org.javalens.mcp/src/org/javalens/mcp/tools/FindDuplicateCodeTool.java`.

**Sketch:** workspace-scoped (or `projectKey`-scoped) clone-detection tool. For every `IMethod` in scope:

1. Build the JDT AST for the method body.
2. Walk the AST and emit a normalized token stream — strip whitespace + comments; replace every local-variable name with `LV1` / `LV2` / `LV3` (in declaration order, scope-local); replace every literal with its kind (`STR` / `INT` / `BOOL` / `NULL`); keep types, operators, control-flow keywords.
3. Hash sliding windows of `minTokens` (default 50) tokens via a rolling hash; group methods sharing N+ window-hashes above the `threshold` (default 0.85 = 85% of the shorter method's tokens).
4. Return clone groups: `{ groups: [{ instances: [{ filePath, line, methodName, tokenCount, similarity }] }] }`. Each group ≥ 2 instances. Sorted by group size + total LOC saved if extracted.

**Why now (vs. Sprint 15+):** the user moved this in mid-planning. Implementation is a self-contained AST + heuristic walk; reuses `org.eclipse.jdt.core.dom` already in the classpath. ~2 days.

**Detection knobs (default off but documented):**
- `minTokens` — minimum method-body token count to consider (default 50, skips trivial methods).
- `threshold` — minimum normalized-token similarity to group (default 0.85).
- `crossProject: bool` — restrict to within-project pairs (false default) vs cross-project workspace search (true).

**Schema honesty:** doc explicitly states this is a *similarity heuristic*, not semantic equivalence. Returns suggestions; agent decides whether the clone is real (e.g. two methods with the same shape but unrelated semantics get flagged — that's a false positive).

**Closes:** Sprint 14 scope amendment 2026-06-04. Was previously "Out of scope" for Sprint 15+.

**Companion workflow** (not in this sprint, but immediately after): the natural `replace_duplicates(cloneGroupId, options)` companion that takes a clone group and runs extract-method + per-clone replace ships in **Sprint 14b** (v1.9.0) alongside the refactoring auto-apply layer. Detection (B.3, this sprint, v1.8.0) ships first; the replacement workflow closes the loop one release cycle later. See [`sprint-14b-refactoring-full-apply.md`](sprint-14b-refactoring-full-apply.md).

**Test:** `FindDuplicateCodeToolTest`:
- `detects_obvious_clone_within_project` — fixture with two `getName()` methods differing only in field name → flagged with similarity ~1.0.
- `respects_minTokens_floor` — two 10-token methods identical → NOT flagged when default 50-token minimum.
- `cross_project_clone` — fixture with method `A.compute()` in P1 and identical `B.compute()` in P2 → flagged in workspace scope.
- `dissimilar_methods_not_grouped` — two unrelated methods → 0 groups.

## Phase C — Gradle path for Ring 3 dep tools (parallel-safe with Phase B)

### C.1 Target platform additions

**File:** `org.javalens.target/org.javalens.target.target` — bump `sequenceNumber` to 6.

**Add:**
- `org.eclipse.buildship.core` (Buildship Gradle integration; ships from the same `download.eclipse.org` repository as JDT).

`resolution:=optional` in `org.javalens.mcp/META-INF/MANIFEST.MF` per the Sprint 13 / Phase C precedent for M2E — tools degrade gracefully when Buildship isn't usable for a project.

### C.2 Gradle path for the three dep tools

**Files (extended):** `AddDependencyTool`, `UpdateDependencyTool`, `FindUnusedDependenciesTool`.

**Sketch:** detection precedence — `pom.xml` first (Maven wins on hybrid projects), `build.gradle` / `build.gradle.kts` second. For Gradle:

- **`add_dependency`** — append a line to the matching configuration block in `build.gradle` / `build.gradle.kts`. Buildship's project model is read-only; mutate file textually preserving user formatting (same approach as the Maven path). Trigger Gradle refresh via `org.eclipse.buildship.core.workspace.GradleBuild.synchronize`.
- **`update_dependency`** — regex-replace the version in the matched dep line. Handle single-quoted, double-quoted, and Kotlin-DSL forms.
- **`find_unused_dependencies`** — parse declared deps from `build.gradle` via regex over `(implementation|api|compileOnly|testImplementation|...)\s+['"]g:a:v['"]`. Same heuristic as Maven (`groupId` prefix match + `artifactId`-as-dotted-suffix). Optional richer mode via Buildship's resolved classpath — deferred to Sprint 15.

**Test extensions:** Gradle counterparts to each Maven test (`AddDependencyToolGradleTest`, `UpdateDependencyToolGradleTest`, `FindUnusedDependenciesToolGradleTest`).

### C.3 Detection precedence

For projects with BOTH `pom.xml` AND `build.gradle*` (rare but possible in monorepos), Maven wins. Document in the tool descriptions.

## Phase D — Doc-scrub + cutover

### D.1 Fork-side doc-scrub

**Same playbook as manager v0.14.1 Stage 5.** Forward-only anonymize. No history rewrite.

**Terms to scrub:**
- `JATS` / `JATS2` / `JATS-ORB-WS` / proprietary-product references
- `ORB Strategy` / `ORB-Strategy` (NOT plain `ORB` alone)
- `trading strategy` / `trading platform`

**Discovery grep** (mirrors manager Stage 5):
```bash
cd /home/harald/CursorProjects/javalens-mcp
grep -rni "jats\|orb.strateg\|orb_jats\|trading.strateg\|trading.platform" \
    --include="*.md" --include="*.java" --include="*.xml" --include="*.properties" \
    --include="*.target" --include="*.json" --include="*.yml" --include="*.toml" \
    --exclude-dir=target --exclude-dir=.git
```

Categories same as manager: (a) narrative scrub, (b) test-fixture renames (`jats` → `alpha`, `orb` → `beta`, `jats2` → `example`), (c) over-matches (review-as-found).

Sprint-history docs (sprint-9 through sprint-13) likely have the same proprietary references the manager docs had. Release notes (v1.4 through v1.7) may have workspace-name examples.

### D.2 Full reactor verify

```bash
cd /home/harald/CursorProjects/javalens-mcp
mvn clean verify
```

Expected after Phases A + B + C:
- `org.javalens.core.tests`: **122 / 122** (unchanged) + new `WorkspaceFileWatcherTest` cases + `ProjectImporterTest` dedupe case.
- `org.javalens.mcp.tests`: ~**446 + 16 new** ≈ 462 (5 from A's new tests, 4 from B.1's RefreshWorkspaceToolTest, 4 from B.2's FQN tests, 4 from B.3's FindDuplicateCodeToolTest, 3 from C's Gradle tests, minus `@Disabled` flips for #1 happy-paths).
- `@Disabled` count drops from 5 (Sprint 13 baseline) → ~2 (the EncapsulateField JDT-bug case stays disabled until upstream fixes; everything else clears).

If the full run reveals interactions between phases (e.g. `refresh_workspace` affecting `compile_workspace` baselines), fix and re-run focused tests for the affected tool(s), then full verify once more.

### D.3 Tag fork v1.8.0

- Bump `Bundle-Version` qualifier across 4 OSGi bundles + product + 8 reactor `pom.xml` files: `1.7.1-SNAPSHOT` → `1.8.0-SNAPSHOT`.
- Wipe stale `~/.m2/repository/org/javalens/.../1.7.x-SNAPSHOT/` to avoid the Sprint 12-pattern stale-bundle issue.
- New [`docs/release-notes/v1.8.0.md`](../release-notes/v1.8.0.md) — covers Phase A bugs FIXED, Phase B new tools, Phase C Gradle path, Phase D scrub.
- [`README.md`](../../README.md) — **MARKETING PIVOT** (Sprint 14 amendment 2026-06-04): restructure so the README leads with the *product* (73→76 tools, IDE-grade Java analysis for agents, battle-tested in production via the companion manager on a multi-project workspace, live feedback loop), and demotes the "fork of pzalutski-pixel" framing to a single "Heritage" section below the roadmap. Keep MIT-attribution to Pzalutski intact in `LICENSE`. Bump tool count `73 → 76` (refresh_workspace + find_*_by_fqn + find_duplicate_code); mention Gradle path for Ring 3.
- [`docs/upgrade-checklist.md`](../upgrade-checklist.md) — flip "Sprint 14 (v1.8.x) follow-up backlog" section items to "Shipped in v1.8.0" and migrate remaining items to a new "Sprint 15 (v1.9.x) follow-up backlog" section.
- `git tag -a v1.8.0 -F docs/release-notes/v1.8.0.md && git push origin master v1.8.0`.
- CI release workflow auto-publishes the GitHub Release.

Manager's release-poller picks up v1.8.0 on its next poll cycle (configured to poll `hw1964/javalens-mcp` by default; no manager-side change needed).

## Critical files

| Path | Phase | Change |
|---|---|---|
| `org.javalens.core/src/org/javalens/core/workspace/WorkspaceFileWatcher.java` | A.2 | ENTRY_CREATE + debounce + WARN log |
| `org.javalens.core/src/org/javalens/core/project/ProjectImporter.java` | A.1 | Classpath dedupe in `addDependencyEntries()` |
| `org.javalens.mcp/src/org/javalens/mcp/tools/RenameSymbolTool.java` | A.3 | Constructor post-pass for class renames |
| `org.javalens.mcp/src/org/javalens/mcp/tools/MoveClassTool.java` | A.4 | Cross-project + modifiedFiles + javadoc-import filter |
| `org.javalens.mcp/src/org/javalens/mcp/tools/CompileWorkspaceTool.java` | A.5 + A.6 | `clean: bool` + `scope: "main"|"test"|"both"` params |
| `org.javalens.core/src/org/javalens/core/workspace/ProjectRegistry.java` | A.7 | `droppedAt` tracking + `PROJECT_KEY_DROPPED` error |
| `org.javalens.mcp/src/org/javalens/mcp/tools/Find*Tool.java` | A.8 | Schema rewrites + nearby-candidate degradation |
| `org.javalens.mcp/src/org/javalens/mcp/tools/junit/JUnitLaunchHelper.java` | A.9 | Maven path + PDE path (full launcher) |
| `org.javalens.mcp/src/org/javalens/mcp/tools/RefreshWorkspaceTool.java` (NEW) | B.1 | Consolidated refresh tool |
| `org.javalens.mcp/src/org/javalens/mcp/tools/fqn/FqnResolver.java` (NEW) | B.2 | FQN-to-IJavaElement helper |
| `org.javalens.mcp/src/org/javalens/mcp/tools/Find*Tool.java` (extended) | B.2 | FQN-overload entry |
| `org.javalens.mcp/src/org/javalens/mcp/tools/FindDuplicateCodeTool.java` (NEW) | B.3 | Normalized-token AST clone detector |
| `org.javalens.target/org.javalens.target.target` | C.1 | Buildship target additions, sequenceNumber → 6 |
| `org.javalens.mcp/META-INF/MANIFEST.MF` | C.1 | `Require-Bundle: org.eclipse.buildship.core;resolution:=optional` |
| `org.javalens.mcp/src/org/javalens/mcp/tools/build/{Add,Update,FindUnused}DependencyTool.java` | C.2 | Gradle paths |
| `docs/bugs.md` | A + D.1 | #6/#7/#8/#9/#10/#11/#12 → FIXED in v1.8.0; #13 → FIXED; doc-scrub |
| `docs/upgrade-checklist.md` | D.3 | Migrate Sprint 14 (v1.8.x) backlog items to "Shipped in v1.8.0" + new Sprint 15 (v1.9.x) section |
| `docs/release-notes/v1.8.0.md` (NEW) | D.3 | Sprint 14 ship notes |
| `README.md` | D.3 | **MARKETING PIVOT**: lead-with-product restructure; demote fork-of-Pzalutski to Heritage section; tool count `73 → 76` |
| `{8 pom.xml + 4 MANIFEST.MF + product}` | D.3 | 1.7.1-SNAPSHOT → 1.8.0-SNAPSHOT |

## Reusable infrastructure already in place

- **`AbstractTool.execute(...)` pattern** — all new tools (RefreshWorkspaceTool, FQN overloads) extend this.
- **`IJdtService.allProjects()` / `getProject(key)`** — B.1's refresh-all-projects iteration uses these.
- **Sprint 11 LTK refactoring base + `AbstractRefactoringTool#initializeJdtManipulation`** — A.4's `MoveClassTool` changes layer on top of this.
- **Sprint 13 codegen via `ASTRewrite`** — A.3's constructor post-pass uses the same JDT-DOM patterns.
- **`TestProjectHelper.loadProjectCopy(...)` / `loadWorkspaceCopy(String...)`** — every Phase A/B test fixture reuses these.
- **`simple-maven` + `simple-pde` fixtures** — already in test-resources; extend with the small synthetic classes A.5/A.6/A.9 need.
- **Tycho release workflow + manager release-poller** — fork v1.8.0 ships the new tools; manager auto-picks them up via the same poller path that picked up Sprints 11 / 12 / 13 / 14 fork releases.

## Verification (sprint exit)

After Phase D.2 + D.3:

1. **Tool count** — `health_check` reports **76 tools** per service.
2. **Bug status** — every previously-OPEN bug from #6 through #13 reads `FIXED in v1.8.0` in `docs/bugs.md`.
3. **Phase A smoke**:
   - `add_project /home/harald/CursorProjects/javalens-mcp` against the fork itself → success (closes #7).
   - `mv` external file into a watched workspace dir → `list_projects` shows it within ~1 s (closes #6).
   - Class rename via `rename_symbol` → constructor renamed (closes #13).
   - Cross-project `move_class` → file moves on disk, javadoc `@link` imports not added (closes #10).
   - `compile_workspace(clean=true)` after a record-shape change → reports real errors (closes #8).
   - `compile_workspace(scope="both")` after a test-source signature change → reports test compile errors (closes #9).
   - Tool call with a key dropped between turns → `PROJECT_KEY_DROPPED` (closes #11).
   - `find_implementations(symbol="com.example.Foo", scope="workspace")` → workspace-scope FQN search returns results (closes #12).
   - `run_tests` against plain Maven fixture → real JUnit launch, structured report (closes #1 Maven path).
   - `run_tests` against PDE bundle fixture with explicit framework → real JUnit launch (closes #1 PDE path).
4. **Phase B smoke**:
   - `refresh_workspace()` after external `Write` → new symbols discoverable; `compile_workspace` reflects the new state.
   - FQN-based `find_*` overloads return correct cross-project results.
5. **Phase C smoke**:
   - `add_dependency` against a Gradle fixture → modifies `build.gradle`, Gradle refresh succeeds.
   - `find_unused_dependencies` against the same → returns plausible candidate list.
6. **Phase D**:
   - `grep -rni "jats|orb.strateg|trading.strateg" --include="*.md" --include="*.java"` → 0 hits in scrubbed scope (acknowledge over-matches in commit body if any remain).
7. **No regression on prior sprints** — full reactor verify (D.2) shows existing tests stay green.

After release:

8. v1.8.0 published as Latest. Manager's release-poller picks it up on its next cycle (verifiable from the manager's dashboard release-status panel).

## Cut line if a Phase A bug or Phase B tool hits unexpected pain

Per the v1.5.2 / v1.6.0 / v1.7.0 precedent (specific tests `@Disabled` with explanatory pointers to `docs/upgrade-checklist.md`): if any individual bug fix runs into infrastructural blockers, mark the affected happy-path tests `@Disabled` with a pointer to a new entry in `docs/upgrade-checklist.md` and ship the rest. Bug stays OPEN in `docs/bugs.md` for the next sprint.

**Bug-level cut candidates** (ranked by acceptability of deferral):

1. **#1 PDE-side full `run_tests`** — Maven path is the bigger user value (most non-fork projects are Maven); PDE path can ship in v1.8.1 if the bundle-detection logic surprises us.
2. **#10 javadoc `@link` filter** — the cross-project relocation + `modifiedFiles` are the higher-value parts; the import-filter can ship in v1.8.1 if it requires deeper JDT internal-API spelunking.
3. **B.2 FQN overload for one of the four `find_*` tools** — ship the ones that work; defer one to v1.8.1 if a specific symbol-resolver path turns out tricky.
4. **B.3 `find_duplicate_code`** — new tool added to Sprint 14 scope mid-planning. Defer to v1.8.1 if the normalized-token-hashing approach turns out noisier than the threshold can fix; the four B.3 tests stay `@Disabled` with pointer to upgrade-checklist.

**Goal:** ship v1.8.0 with at least the consolidated `refresh_workspace` tool + the two `compile_workspace` correctness fixes (#8 + #9) + bug #7 fix even if other Phase A items slip. Those three close the agent-trust regression that's the sprint's core theme.

## Build / test commands

`javalens-mcp` (during Sprint 14 — focused only):

```bash
cd /home/harald/CursorProjects/javalens-mcp

# Per-tool focused unit test (~2 min):
mvn -pl org.javalens.mcp.tests -am verify -Dtest=RefreshWorkspaceToolTest

# Compile-only loop (~30 s):
mvn -pl org.javalens.mcp -am compile

# End of sprint, ONCE (~20 min):
mvn clean verify
```

## Definition of Done

- [ ] Phase A: 9 bugs fixed (#6 / #7 / #8 / #9 / #10 / #11 / #12 / #13 + #1 full fix), all focused tests green.
- [ ] Phase B: `refresh_workspace` tool shipped, FQN overloads for all four `find_*` tools shipped, `find_duplicate_code` tool shipped, focused tests green.
- [ ] Phase C: 3 Gradle paths shipped for the Ring 3 dep tools, focused tests green.
- [ ] Phase D.1: fork-side doc-scrub complete; re-grep returns 0 PROPRIETARY hits in scrubbed scope.
- [ ] Phase D.2: full reactor `mvn clean verify` green (122 core + ~458 mcp + ~2 `@Disabled`).
- [ ] Phase D.3: Fork v1.8.0 tagged + published. Manager's release-poller picks it up on next cycle.
- [ ] Per-workspace tool count is **76** (`health_check` confirms).
- [ ] Zero AI-attribution boilerplate in any commit, release note, or doc produced during the sprint.
- [ ] No regression on Sprint 11 / 12 / 13 fixtures (existing 122 core + ~446 mcp tests stay green).
- [ ] `docs/upgrade-checklist.md`'s "Sprint 14 (v1.8.x) follow-up backlog" section migrated to "Shipped in v1.8.0" + new "Sprint 15 (v1.9.x) follow-up backlog" section holds the deferred items (Android, Fowler, orchestration, HTTP/SSE, M2E richer find_unused, find_duplicate_code, copy_class/wrap_class, pre_edit_impact).
