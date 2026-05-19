# JavaLens MCP — Bug Tracker

Living log of issues found during real-world usage by AI agents and humans.
Append new bugs at the **top**. Status values: `OPEN`, `IN_PROGRESS`, `FIXED in vX.Y.Z`, `WONTFIX`, `DUPLICATE`.

For each entry include: ID, date observed, severity, reproducer, expected vs actual, environment, and (when known) suspected root cause.

---

## #12 — `find_implementations` / `find_field_writes` require exact `(filePath,line,column)`; no FQN/type-scoped path, and the constraint is undocumented

- **Status:** OPEN
- **Date observed:** 2026-05-15 / 2026-05-13 (EXECSIM-Java sessions)
- **Reporter:** Claude (Opus 4.7) via `jl-jats-orb-ws`, recorded in `~/CursorProjects/EXECSIM-Java/docs/mcp_feedback.md`
- **Server version:** 1.7.x (2.0.0-SNAPSHOT health string)
- **Severity:** MEDIUM — forces a `Bash grep` fallback for the single most common navigation question ("who implements / writes X?"); the misleading error reads like a caller mistake.

### Reproducer

```
find_implementations(typeName="com.execsim.execution.ExecutionContext")
→ INVALID_PARAMETER: Required parameter missing: filePath

find_field_writes  targeted at OrbExecutionStrategy$Slot.pendingEntry by (line,col)
→ "Symbol at position is not a field (found: Method)"  (coords off by a few lines; refuses instead of offering near candidates)
```

### Expected

Either an FQN/type-scoped lookup (`find_implementations(typeName=…)` searches the whole workspace) OR a schema description that states the `(filePath,line,column)` requirement up-front so the caller doesn't infer a type-scoped API that doesn't exist.

### Actual

Whole `find_*` family is position-only. `find_implementations`'s schema says "Find implementations" with no mention that the lookup is file-scoped. `find_field_writes` refuses on minor coordinate misalignment rather than returning candidates near the position.

### Suggested fix

Bug-side: disclose the `filePath`/coords requirement in each `find_*` tool's schema description (same fix-class as v1.7.1 #3). `find_field_writes`: when the position resolves to a non-field, return nearby field candidates instead of a hard refusal. The deeper FQN-entry-point ask is a **feature** tracked in `upgrade-checklist.md` (v1.8.x backlog) — this entry is only the schema-honesty + graceful-degradation defect.

### Cross-reference

- Feature counterpart (FQN `find_*` overload) — `upgrade-checklist.md` Sprint 14 (v1.8.x) backlog. Most-repeated ask across every EXECSIM session.

---

## #11 — Acquired `projectKey` silently invalidated by workspace-state mutation; fails with misleading `INVALID_PARAMETER`

- **Status:** OPEN
- **Date observed:** 2026-05-15 / 2026-05-16 (EXECSIM-Java Sprint 3 / 3.1)
- **Reporter:** Claude (Opus 4.7) via `jl-jats-orb-ws`
- **Server version:** 1.7.x
- **Severity:** MEDIUM — a long-lived caller's `projectKey` goes stale mid-session with an error that reads like a caller bug, not a state change.

### Reproducer

1. `list_projects` → acquire `projectKey="execsim-java"`.
2. (Between turns, another managed client / manager re-balances the workspace; `execsim-java` is dropped, only `orb` remains, sourceFileCount 0.)
3. `compile_workspace(projectKey="execsim-java")` → `INVALID_PARAMETER: Unknown projectKey`.

### Expected

A structured, self-describing error: `PROJECT_KEY_DROPPED: project 'execsim-java' was unloaded (by client/manager) at <ts>; re-acquire via list_projects` — OR transparently succeed against the currently-loaded equivalent.

### Actual

`INVALID_PARAMETER: Unknown projectKey` — indistinguishable from the caller passing a typo'd key.

### Suspected root cause

Multi-tenant JVM model (cf. v1.7.1 #5): each MCP client spawns its own JVM; the manager mutates loaded-project sets independently. No epoch/version on `projectKey`, no "this key was valid but is now retired" distinction.

### Suggested fix

Distinct error code `PROJECT_KEY_DROPPED` with the unload reason + timestamp; keep `INVALID_PARAMETER` strictly for never-valid keys. Optionally an epoch token in `list_projects` output so callers can detect a state shift before a keyed call fails.

---

## #10 — `move_class` cross-project deficiencies (no physical relocation, empty `modifiedFiles`, test-source consumers missed, no back-edge warning)

- **Status:** OPEN
- **Date observed:** 2026-05-11 (EXECSIM-Java Sprint 1 Phase 1)
- **Reporter:** Claude (Opus 4.7) via `jl-jats-orb-ws`
- **Server version:** 1.7.x
- **Severity:** MEDIUM-HIGH — silently produces broken cross-project state; the `~30 class` migration it was meant to automate falls back to manual `mv`/`rmdir`/`grep` per class.

### Reproducer

`move_class(filePath="ORB-Java/.../indicators/TechnicalIndicators.java", targetPackage="com.execsim.training.core.features.indicators")`

### Actual (four distinct defects)

1. **No physical relocation across projects.** File's `package` decl is rewritten but the file stays in the originating project's source tree (`ORB-Java/src/.../com/execsim/...`), an alien `com.execsim.*` dir inside ORB-Java. Manual `mv` + `rmdir` required.
2. **`modifiedFiles: []` always empty** despite the package decl + multiple consumer imports being rewritten. No way for the caller to verify the change set.
3. **Cross-project consumer updates skip test sources.** `EXECSIM-Java` test `OrbModelRankingParityIT.java` kept the old import; 5/6 consumers updated, the test straggler not.
4. **No back-edge pre-flight.** When the origin project still references the moved class, the move silently yields broken-standalone-compile or a circular Maven dep. No warning, no opt-in flag.

### Suggested fix

- `targetProjectKey` param (or auto-detect from `targetPackage` matching another project's source root) → physically move the file.
- Populate `modifiedFiles` with the real change set.
- Include test sources in the consumer-update scan; document the scope.
- Pre-flight `find_references` on the source FQN scoped to the origin project; if internal back-edges exist, warn (or refuse without `--allow-back-edges`) and point at strangler-fig / branch-by-abstraction options.

### Cross-reference

- Related feature asks (`copy_class`, `wrap_class`) — `upgrade-checklist.md` v1.8.x backlog. Together they form the strangler-fig cross-project-migration toolset.

---

## #9 — `compile_workspace` never compiles test sources — reports `errorCount:0` while `mvn test-compile` fails

- **Status:** OPEN
- **Date observed:** 2026-05-17 (EXECSIM-Java Sprint 4 Stage 8.4)
- **Reporter:** Claude (Opus 4.7) via `jl-jats-orb-ws`
- **Server version:** 1.7.x
- **Severity:** HIGH — `compile_workspace` is the documented fast-feedback "is it green?" tool; a signature change that breaks N test files reports clean, so a caller advances on a broken tree.

### Reproducer

1. Add a parameter to `CandidateLabelMachine` constructor (main-source edit).
2. `compile_workspace(projectKey="execsim-java")` → `{errorCount: 0}`.
3. `mvn test-compile` → 8 errors across 7 test files (old arity at call sites).

### Expected

`compile_workspace` either compiles test sources by default, or exposes `scope: "main"|"test"|"both"` and documents that the default excludes tests.

### Actual

Silently main-source-only. The "0 errors" is misleading for any signature change that affects test callers — the exact opposite of what "compile workspace" implies.

### Suggested fix

Add `scope: "main"|"test"|"both"` (default `"both"`, or at minimum document `"main"`-only and make `"both"` a one-liner). Compile test source roots via the same `IJavaProject.build` path already used for main.

### Cross-reference

- Sibling of #8 (both make `compile_workspace`'s green untrustworthy; different root cause — #8 is stale incremental cache, #9 is scope-never-included).

---

## #8 — `compile_workspace` false-pass on record / signature shape changes (JDT stale incremental cache)

- **Status:** OPEN
- **Date observed:** 2026-05-16 (EXECSIM-Java Sprint 3.1 Stage 2)
- **Reporter:** Claude (Opus 4.7) via `jl-jats-orb-ws`
- **Server version:** 1.7.x
- **Severity:** HIGH — the build-status signal itself is wrong (worse than a stale *navigation* index); a caller treating `errorCount:0` as a green light advances with code that does not compile.

### Reproducer

1. Extend `LabelCacheKey` from a 5-field record to a 7-field record (canonical constructor arity changes; 3 call sites now wrong).
2. `compile_workspace(projectKey="execsim-java")` → `{errorCount: 0, diagnostics: []}` ✗ false pass.
3. `mvn compile` (non-clean) → also false pass (Maven reuses cached class files).
4. `mvn clean compile test-compile` → 3 real compile failures ✓.

### Suspected root cause

JDT's incremental compiler reuses class files compiled against the OLD record/constructor signature; it doesn't invalidate downstream consumers' bytecode when the record's canonical constructor shape changes. No JDT equivalent of Eclipse IDE's `Project → Clean → Build All` is exposed.

### Suggested fix

`clean_compile_workspace` tool, OR `clean: true` on `compile_workspace`, that drops JDT incremental state and full-recompiles. Ideally fold into the `refresh_workspace`/`reindex` tool (see `upgrade-checklist.md` v1.8.x backlog) so one tool covers: refresh-from-disk + incremental-cache invalidation + full recompile. Narrower auto-fix: invalidate the cache when a record's canonical constructor (or an interface/public-method signature) changes.

### Cross-reference

- Sibling of #9. Both feed the "`compile_workspace` is advisory, not authoritative during signature changes" conclusion in `~/CursorProjects/EXECSIM-Java/docs/mcp_feedback.md`.
- The `reindex`/`refresh_workspace` feature must invalidate the *incremental compile* cache, not just the symbol index, to close this — recorded in the v1.8.x backlog.

---

## #7 — `add_project` on the fork's own multi-module repo fails with "Build path contains duplicate entry: gradle-tooling-api-8.10.jar"

- **Status:** OPEN
- **Date observed:** 2026-05-11 (during v1.7.1 post-fix smoke)
- **Reporter:** Claude (Opus 4.7), via `jl-javalens-ws`
- **Server version:** 1.7.1
- **Severity:** MEDIUM — blocks loading the fork's own source as a workspace project. Workaround exists (load individual modules or other projects) but the fork-developing-itself flow is broken.

### Reproducer

```
mcp__jl-javalens-ws__add_project /home/harald/CursorProjects/javalens-mcp
```

### Actual

```jsonc
{
  "success": false,
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "Internal error: Build path contains duplicate entry: 'home/harald/CursorProjects/javalens-mcp/org.javalens.core/lib/gradle-tooling-api-8.10.jar' for project 'javalens-javalens-mcp-32b2baaa'",
    "hint": "This may be a bug. Check server logs for details."
  }
}
```

### Expected

Project loaded successfully with the fork's multi-module structure (`org.javalens.core`, `org.javalens.mcp`, `org.javalens.mcp.tests`, `org.javalens.target`, `org.javalens.launcher`, `org.javalens.product`).

### Suspected root cause

JDT's `IJavaProject.setRawClasspath()` rejects classpath arrays with duplicate `IClasspathEntry` paths. The fork's multi-module Maven+PDE hybrid layout has `gradle-tooling-api-8.10.jar` referenced twice — most likely once from `org.javalens.core/lib/` (the directory scan) AND once from `org.javalens.core/.classpath` (explicit `<classpathentry kind="lib">`). `ProjectImporter.addDependencyEntries()` doesn't deduplicate before calling `setRawClasspath()`.

May affect other Tycho-style hybrid repos with both `lib/` directories and explicit `.classpath` lib entries. Worth checking how widespread.

### Suggested fix

In `ProjectImporter.addDependencyEntries()` (or the upstream classpath-collection code), dedupe `IClasspathEntry` instances by their resolved absolute path before passing to `setRawClasspath()`. Or alternatively: track already-added paths in a `Set<Path>` during collection and skip duplicates.

### Cross-reference

- Surfaced during the v1.7.1 release smoke; the fork's own `jl-javalens-ws` workspace can't load itself with the current code.
- Independent of v1.7.1 fixes; the duplicate-entry path would have been present in v1.7.0 too. Just wasn't tripped on the JATS-ORB-WS workspace's project set.

---

## #6 — `WorkspaceFileWatcher` doesn't reflect live `workspace.json` edits

- **Status:** OPEN
- **Date observed:** 2026-05-11 (during v1.7.1 post-fix smoke)
- **Reporter:** Claude (Opus 4.7)
- **Server version:** 1.7.1
- **Severity:** MEDIUM — undermines the manager's add-project-while-services-running UX. User adds a project in the manager UI → manager writes the new entry to `workspace.json` → currently-spawned MCP JVMs (Cursor, Claude Code, etc.) never see the update; they only pick it up on next JVM restart.

### Reproducer

1. With both manager and Claude Code running, ensure a workspace's `<workspace>/workspace.json` lists no projects.
2. Call `mcp__jl-<workspace>__list_projects` → returns `{ projects: [] }`.
3. In the manager UI, add a project to the workspace (manager writes the updated `workspace.json`).
4. Wait a few seconds. Re-call `list_projects` from the same Claude Code session.
5. Observe: still `{ projects: [] }`.

### Expected

`WorkspaceFileWatcher.start()` (in `org.javalens.core.workspace.WorkspaceFileWatcher`, Sprint 10 v1.4.0) is documented as performing "synchronous initial load + arm watcher thread". The watcher should fire on file modify events and incrementally load any new projects.

### Actual

The watcher either:
- Doesn't fire on the file-change event from the manager's atomic-rename write pattern (manager may write to a `.tmp` file then `rename()`), or
- Fires but the load path silently fails, or
- The watcher thread isn't actually running (init bug).

Health check shows `loaded: true, projectCount: 0` after the file edit, suggesting the watcher path is reached but no project gets added.

### Suspected root cause

`WatchService` semantics on Linux: atomic-rename writes generate `ENTRY_CREATE` events on the *new* inode, not `ENTRY_MODIFY` on the existing path. If `WorkspaceFileWatcher` only listens for `ENTRY_MODIFY`, manager-written updates are missed. Same gotcha as many other `WatchService`-based config reloaders.

Alternative: the watcher fires correctly but the load path encounters an exception that's swallowed without logging. Worth checking `WorkspaceFileWatcher.handleEvent()` / equivalent for missing error logging.

### Suggested fix

1. Listen for both `ENTRY_MODIFY` AND `ENTRY_CREATE` on the workspace directory; on either, re-read `workspace.json` and reconcile projects.
2. Surface any reconciliation errors to a WARN-level log so a stale watcher is debuggable.
3. Optional but recommended: add a debounce (200ms) so a fast tmp-write-then-rename pair doesn't trigger two reloads.

### Cross-reference

- The Sprint 10 v1.4.0 feature that this watcher implements is the reason the v1.7.1 walk-up fix works at startup. Live updates are orthogonal.
- Workaround: restart the affected MCP client (kill javalens.jar PID; let parent respawn).

---

## #5 — javalens-mcp spawned by non-manager MCP clients starts with zero projects loaded

- **Status:** FIXED in v1.7.1
- **Date observed:** 2026-05-11
- **Reporter:** Claude (Opus 4.7), diagnosing during v1.7.1 planning session
- **Server version:** 1.7.0
- **Severity:** HIGH — every non-manager MCP client (Cursor, every Claude Code session, every Claude Desktop instance) operates against an empty workspace until the user manually calls `add_project` per project. Defeats the "deploy MCP entries into clients" usability story; agents see empty `list_projects` and look broken.

### Environment

- MCP server config (per `~/.cursor/mcp.json`, `~/.claude.json`, etc.):
  ```json
  "jl-javalens-ws": {
    "command": "java",
    "args": ["-jar", ".../javalens.jar", "-data", ".../workspaces/JAVALENS-WS"],
    "env": { "JAVALENS_WORKSPACE_NAME": "JAVALENS-WS" }
  }
  ```
- Manager workspace started in UI (visible in tray as Running).

### Reproducer

Start a fresh Claude Code session. Call `mcp__jl-javalens-ws__list_projects`. Observe `{ projects: [] }` despite the manager's UI showing the same workspace fully populated.

### Actual

```jsonc
{ "success": true, "data": { "projects": [] } }
```

### Expected

Same project list the manager has registered for that workspace.

### Root cause (revised after investigation)

The auto-load mechanism **already exists** (Sprint 10 v1.4.0): `JavaLensApplication.autoLoadProjects()` reads `<-data>/workspace.json` and calls the project-load path for each entry. The manager has been writing `<workspace>/workspace.json` since v1.4.0 — verified, files exist with the correct project lists.

The bug is the **interaction with the session-isolation wrapper.** `JavaLensLauncher` (the Main-Class for `javalens.jar`) generates a UUID and rewrites `-data <workspace>` to `-data <workspace>/<uuid>` *before* delegating to the Equinox launcher. So:

- Manager writes: `/home/harald/.cache/javalens-manager/workspaces/JAVALENS-WS/workspace.json`
- OSGi's `osgi.instance.area` resolves to: `/home/harald/.cache/javalens-manager/workspaces/JAVALENS-WS/<uuid>/`
- `autoLoadProjects()` looks for: `<uuid>/workspace.json` — **does not exist**
- Falls through to `JAVA_PROJECT_PATH` env var (not set), exits with empty project list.

Stdio-per-client + 4 JVMs per workspace is just the symptom magnifier — even one JVM has this bug. Confirmed by inspecting `JavaLensLauncher.main()` (`org.javalens.launcher/.../JavaLensLauncher.java:30-66`) which performs the UUID injection unconditionally.

### Fix (v1.7.1)

`JavaLensApplication.autoLoadProjects()` walks one directory up if `workspace.json` isn't found in the immediate OSGi data dir. The parent is the workspace root the manager writes to. One-file change in [`org.javalens.mcp/src/org/javalens/mcp/JavaLensApplication.java`](../org.javalens.mcp/src/org/javalens/mcp/JavaLensApplication.java) — new private helper `findWorkspaceJson(Path)` plus the call-site swap inside `autoLoadProjects()`. No manager-side change required. Backward-compatible with direct invocations that don't go through `JavaLensLauncher` (the immediate-dir check fires first).

### Alternative fix (NOT taken)

Either (a) write `workspace.json` into each UUID subdir from the launcher (more I/O on startup) or (b) HTTP/SSE single-tenant service (bigger refactor, v1.8.0+ candidate). Walking up one level is the smallest correct fix.

---

## #4 — `buildSystem` reports `"unknown"` for Eclipse PDE bundles even when `.classpath` parses successfully

- **Status:** FIXED in v1.7.1
- **Date observed:** 2026-05-02
- **Reporter:** Claude (Opus 4.7) via `jl-jats-orb-ws` workspace
- **Server version:** 2.0.0-SNAPSHOT
- **Severity:** LOW — labeling/UX issue; functionality works.

### Reproducer

```
mcp__jl-jats-orb-ws__add_project /home/harald/Projects/jats2/com.jats2.model
mcp__jl-jats-orb-ws__list_projects
```

### Actual

```jsonc
{
  "projectKey": "com-jats2-model",
  "projectPath": "/home/harald/Projects/jats2/com.jats2.model",
  "buildSystem": "unknown",
  "sourceFileCount": 1030,
  "packageCount": 123,
  "classpathEntryCount": 29,
  ...
}
```

`buildSystem: "unknown"` despite the project being a perfectly recognizable Eclipse PDE bundle (it has `META-INF/MANIFEST.MF`, `.classpath`, `.project`, and `build.properties` at the root). The README says: *"Supports Maven projects (pom.xml), Gradle projects, **Eclipse projects (.classpath src/lib entries honored when present)**, and Plain Java projects with src/ directory."* So PDE is explicitly supported — but the reported label gives the impression that the loader fell back to a generic mode.

### Expected

`buildSystem: "eclipse-pde"` (or `"eclipse"`, `"pde"`, etc.). Indexing actually works (1030 files / 123 packages, classpath resolved with 29 entries, all `find_references` / `get_type_hierarchy` calls return correct results across the bundle), so it really is being recognized — just labeled `"unknown"` in the response.

### Why it matters

A caller looking at `list_projects` and seeing `"unknown"` reasonably suspects the project failed to load properly. Wasted investigation cycles. Also makes it harder to write tools that branch on `buildSystem` (e.g. "if maven, use these flags; if pde, use those") because PDE bundles fall into the same bucket as actual unknown-shape projects.

### Suggested fix

- If `META-INF/MANIFEST.MF` with `Bundle-SymbolicName` exists at project root → `"eclipse-pde"`.
- Else if `.classpath` exists → `"eclipse"`.
- Else if `pom.xml` → `"maven"`, etc. (already works).
- Else `"unknown"`.

---

## #3 — `run_tests` schema description for `scope.kind="method"` mismatches validation

- **Status:** FIXED in v1.7.1
- **Date observed:** 2026-05-01
- **Reporter:** Claude (Opus 4.7)
- **Server version:** 2.0.0-SNAPSHOT
- **Severity:** LOW — documentation/UX bug; users get a clear error message but the schema docs steer them wrong first.

### Reproducer

The `run_tests` tool's description says (excerpted):

```
Inputs:
- scope.kind — "method" | "class" | "package".
- scope.filePath / line / column — for method/class; zero-based.
- scope.typeName — alternative to filePath for class scope.
- scope.methodName — for method scope.
```

Reading that, the natural call shape is:

```jsonc
{
  "scope": {
    "kind": "method",
    "filePath": "/abs/path/Test.java",
    "methodName": "testFoo"
  }
}
```

### Actual

```jsonc
{
  "success": false,
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "Invalid parameter 'scope': kind='method' requires either {typeName, methodName} or {filePath, line, column}."
  }
}
```

The validation message is clear and correct, but it contradicts the schema description: the description says `methodName` is for method scope (implying it pairs with `filePath`), while validation requires `methodName` only with `typeName`, never with `filePath`.

### Expected

Either (a) update the schema description to spell out the actual valid combinations (`{typeName, methodName}` OR `{filePath, line, column}`, never `{filePath, methodName}`), or (b) make the validation accept `{filePath, methodName}` (resolve method by name within the file).

Option (a) is cheaper. Option (b) would be a small UX win — `{filePath, methodName}` is a natural shape and saves the caller from looking up line/column.

### Suggested fix

Schema description rewrite:

```
- scope.kind — "method" | "class" | "package".
- For kind="method": pass either
    {typeName, methodName}                      (find method by FQN + name)
    or {filePath, line, column}                 (find method at cursor position).
- For kind="class": pass either
    {typeName}                                  (FQN)
    or {filePath, line, column}                 (find class at cursor position).
- For kind="package": pass {packageName}.
```

---

## #2 — `search_symbols` leaks javalens-manager cache file paths into user-visible results

- **Status:** FIXED in v1.7.1
- **Date observed:** 2026-05-01
- **Reporter:** Claude (Opus 4.7) via `jl-jats-orb-ws` workspace
- **Server version:** 2.0.0-SNAPSHOT
- **Severity:** MEDIUM — pollutes search results with non-source paths, wastes user/agent attention, can mislead refactor tooling that expects every result to be a real source location.

### Reproducer

```jsonc
{
  "tool": "search_symbols",
  "arguments": { "query": "AlpacaFullProvider", "kind": "Class" }
}
```

### Actual

```jsonc
{
  "results": [
    {
      "name": "AlpacaFullProvider",
      "kind": "Class",
      "filePath": "/home/harald/.cache/javalens-manager/workspaces/JATS-ORB-WS/6d65bfe1/javalens-com.jats2.model-7e6f70c7",
      "qualifiedName": "com.jats2.model.provider.alpol.alpaca.AlpacaFullProvider",
      "package": "com.jats2.model.provider.alpol.alpaca"
    },
    {
      "name": "AlpacaFullProvider",
      "kind": "Class",
      "filePath": "/home/harald/.cache/javalens-manager/workspaces/JATS-ORB-WS/6d65bfe1/javalens-strategies_orb-7e6f70c7",
      "qualifiedName": "com.jats2.model.provider.alpol.alpaca.AlpacaFullProvider",
      "package": "com.jats2.model.provider.alpol.alpaca"
    },
    {
      "name": "AlpacaFullProvider",
      "kind": "Class",
      "filePath": "/home/harald/Projects/jats2/com.jats2.model/src/com/jats2/model/provider/alpol/alpaca/AlpacaFullProvider.java",
      "line": 53,
      "column": 6,
      ...
    }
  ]
}
```

The first two entries have the cache directory as their `filePath` and **no `line` / `column`** — they're not navigable source locations. The third is the real file. Same pattern observed for `TransactionProvider` (3 results: 2 cache + 1 real) and several other queries.

Inconsistent across queries: a few queries (`SlotManager`, `IExecutionAlgo`) returned only the real source path with no cache duplicates. So the leak isn't universal — appears to depend on whether the symbol resolves to a class that's also referenced by a sibling project's classpath entry (here `strategies_orb` depends on `com.jats2.model` JAR, so the same class shows up under both indices' cache snapshots).

### Expected

Either (a) suppress cache-path entries from `search_symbols` results entirely (deliver only real source-file matches with `line` / `column`), or (b) deduplicate by `qualifiedName` and prefer the entry that has a real source location.

### Why it matters

- Agents iterating over results to refactor each call site will hit cache paths that aren't real files. `Read` on the cache path returns binary or fails. Refactor tools that rely on `filePath:line:column` from search results break.
- Doubles or triples the result count for common types — agents waste tokens skimming duplicates.
- Hard to filter client-side because the `package` and `qualifiedName` are identical to the real entry — only the `filePath` shape distinguishes.

### Suggested fix

In the search-symbols handler, after collecting candidate entries, drop those whose `filePath` lacks `line`/`column` AND points into `~/.cache/javalens-manager/`. Or de-duplicate by `(qualifiedName, kind)` and keep only the entry that has source coordinates.

---

## #1 — `run_tests` returns `INTERNAL_ERROR` (NPE on `Bundle.getHeaders()`) for plain Maven projects

- **Status:** FIXED in v1.7.1 *(workaround dispatch; full launch path tracked for v1.8.0)*
- **Date observed:** 2026-05-01
- **Reporter:** Claude (Sonnet 4.6 / Opus 4.7) via `jl-jats-orb-ws` workspace
- **Server version:** 2.0.0-SNAPSHOT (per `health_check`)
- **Severity:** HIGH — blocks the documented MCP-driven TDD workflow for any non-Eclipse-PDE Maven project; agents are forced to fall back to `mvn test` via the Bash tool, which defeats the purpose of having a typed MCP test runner.

### Environment

- Workspace: `JATS-ORB-WS` with five loaded projects.
  - `strategies-orb` — Maven, `<sourceDirectory>strategies/src</sourceDirectory>` override.
  - `com-jats2-model` — Eclipse PDE bundle (`buildSystem: "unknown"`).
  - `execsim-java` — Maven.
  - `com-jats2-gateways-alpol` — Maven, plain `src/main/java` + `src/test/java` layout.
  - `orb-java` — Maven.
- Target project for the failing call: `com-jats2-gateways-alpol` (plain Maven, JUnit 4.12, Mockito 5.5.0, Java 21).

### Reproducer

```jsonc
// MCP request
{
  "tool": "run_tests",
  "arguments": {
    "scope": {
      "kind": "method",
      "typeName": "com.jats2.gateways.alpol.alpaca.orders.OrderProcessorTest",
      "methodName": "cancelBeforePendingNew_isQueuedAndDrained"
    },
    "framework": "junit4",
    "projectKey": "com-jats2-gateways-alpol",
    "timeoutSeconds": 60
  }
}
```

### Actual

```jsonc
{
  "success": false,
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "Internal error: Cannot invoke \"org.osgi.framework.Bundle.getHeaders()\" because \"bundle\" is null",
    "hint": "This may be a bug. Check server logs for details."
  }
}
```

### Expected

Test runs successfully and returns the parsed pass/fail report shape documented in the tool's description (`{ framework, projectsTested, summary{...}, failures[...], stdoutTail, stderrTail }`).

The same test runs cleanly via `mvn test -Dtest='OrderProcessorTest#cancelBeforePendingNew_isQueuedAndDrained'` in the project root:

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Suspected root cause

The NPE on `org.osgi.framework.Bundle.getHeaders()` strongly suggests the JDT-LTK launching delegate (or its surrounding Equinox bootstrap inside javalens-mcp) is trying to resolve a *bundle* for the test project, finds none (because plain Maven projects are not OSGi bundles — they have no `MANIFEST.MF` headers, no PDE `.classpath` shape), and dereferences `null` instead of falling back to a non-OSGi launch path.

Likely call site: somewhere in `org.javalens.mcp` or `org.javalens.core` that wraps `org.eclipse.jdt.junit.launcher.JUnitLaunchConfigurationDelegate` (or the Tycho equivalent) and assumes the project resolves to an `IBundle`. Worth grepping the javalens codebase for `Bundle.getHeaders()` / `getBundle()` calls around the test launcher and adding a null check + plain-Maven fallback.

The symmetric tool `compile_workspace` works correctly on the same project (returns `errorCount: 0` in <1 s), so the JDT compile-side path is fine — the bug is specific to the JUnit launching path.

### Suggested fix shape

In the `run_tests` handler, before assuming the target is an OSGi bundle:
- Check whether the `IProject` has the `PluginNature`. If not, skip the bundle-headers lookup entirely.
- Branch on build system (`maven` / `gradle` / `unknown` per `list_projects`) and use the right launching delegate:
  - Plain Maven / Gradle → `JUnitLaunchConfigurationDelegate` directly with the project's `IClasspathContainer` resolved by m2e / Buildship.
  - Eclipse PDE → Tycho-aware Surefire delegate (current path).
- Surface a more actionable error message when the project type genuinely can't be resolved (e.g. `"target project com-jats2-gateways-alpol is not an OSGi bundle and has no JUnit launcher available"`) instead of bubbling an NPE as `INTERNAL_ERROR`.

### Workaround

Agents should fall back to running tests via the `Bash` tool with `mvn test -Dtest='...'` for now. This works for any Maven project on the workspace but loses the structured pass/fail summary and stack-trace parsing the MCP tool would otherwise provide. Per memory note `feedback_prefer_mcp.md`, agents should still try `run_tests` first and only fall back when this NPE fires.

### Cross-reference

- Workaround called out in: `~/CursorProjects/strategies_orb/.claude/plans/fizzy-watching-narwhal.md` (Phase 1A verification section).
- Production code being tested when this was hit: `com.jats2.gateways.alpol.alpaca.orders.OrderProcessor` cancel-defer-until-NEW logic for Alpaca workaround.
