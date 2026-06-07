# Fork Sprint 16 — GOJA rebrand + service consolidation

> **Status: planned 2026-06-07.** The brand-cutover sprint. Closes the **javalens-mcp** brand at v1.10.0 (Sprint 15 — modernisation sweeps) and starts the **GOJA** product line at **goja-mcp v1.0** (fresh start, fresh version line).
>
> **Target version: goja-mcp v1.0** (intentionally NOT v2.0 of javalens-mcp — the rebrand is a clean break, new product identity, new version line).
>
> **Predecessor:** [`sprint-15-modernisation-sweeps.md`](sprint-15-modernisation-sweeps.md) → javalens-mcp v1.10.0 (final javalens release).
>
> **Successor:** [`sprint-17-fowler-smell-detection.md`](sprint-17-fowler-smell-detection.md) → goja-mcp v1.1 (first content release under GOJA — the 18 Fowler smell-detection tools).

## Goal

Ship **goja-mcp v1.0** as the renamed, repackaged, namespace-cleaned successor to javalens-mcp v1.10.0. Three parallel work streams in one sprint:

1. **Brand cutover** — repo slug rename, Java package rename (`org.javalens.*` → `org.goja.*`), bundle ID rename, jar name change, MCP service prefix change, settings/data dir rename, all visible UI strings.
2. **Service consolidation for Antigravity tool cap** — Antigravity's MCP client caps total tools at ~100 across all configured servers. Today javalens-mcp ships 75; modernisation (Sprint 15) adds 6 → 81. Headroom is shrinking. This sprint consolidates the existing tool surface into a smaller set of **parametric tools** (precedent: Sprint 11 v1.5.0 collapsed 13 narrow tools into `find_pattern_usages(kind, query)` + `find_quality_issue(kind, ...)`). Target: stay below 80 tools after GOJA v1.0 so Sprints 17-20 have room to add 30+ detection tools without hitting the cap.
3. **Version line reset** — goja-mcp v1.0 (not v2.0 of javalens). The brand reset is a clean product identity break; v1.0 tells the world this is a new product, not a continuation. javalens-mcp v1.10.0 retires; users migrate by deploying the goja-mcp jar.

## Requirements

### Brand cutover (Stream 1)

**GitHub:**
- Rename repos: `haraldwegner/javalens-mcp` → `haraldwegner/goja-mcp`, `haraldwegner/javalens-manager` → `haraldwegner/goja-studio`
- Update all cross-references in fork README, sprint docs, release notes
- Update the manager's release-poller URL: `haraldwegner/javalens-mcp/releases` → `haraldwegner/goja-mcp/releases`
- Archive `haraldwegner/javalens-mcp` (post-migration, leave as redirect)

**Java code:**
- Package rename: `org.javalens.core` → `org.goja.core`, `org.javalens.mcp` → `org.goja.mcp`, `org.javalens.product` → `org.goja.product`, `org.javalens.target` → `org.goja.target`, `org.javalens.launcher` → `org.goja.launcher`. All ~8 modules.
- Class rename: `JavaLensApplication` → `GojaApplication`, `JavaLensLauncher` → `GojaLauncher`, `IJavaLensService` → `IGojaService` (or `IJdtService` stays — that's already neutral), etc. All `JavaLens*` prefixed classes become `Goja*`.
- Bundle IDs in `MANIFEST.MF`: `Bundle-SymbolicName: org.javalens.*` → `Bundle-SymbolicName: org.goja.*`. All 4 OSGi bundles.
- `Require-Bundle` declarations in dependent manifests update to match.
- Pom artifactId rename: `org.javalens.core` → `org.goja.core`, etc.
- Product file rename: `org.javalens.product` → `org.goja.product`. Product `uid="org.javalens.product"` → `uid="org.goja.product"`.

**Artifact + runtime:**
- Jar name: `javalens.jar` → `goja.jar` (consumed by manager's release-poller; coordinate with manager release window)
- MCP service ID prefix: `jl-` → `goja-` (e.g. `jl-jats-orb-ws` → `goja-jats-orb-ws`). User-visible in MCP client configs.
- Settings dir: `~/.config/javalens-manager/` → `~/.config/goja-studio/` (manager-side rename in parallel)
- Cache dir: `~/.cache/javalens-manager/` → `~/.cache/goja-studio/`

**Visible UI / docs:**
- README full rewrite: headline, install one-liners, all references to "JavaLens" → "GOJA", new positioning paragraph (Gods of Java framing as backronym; sober technical tagline as primary copy until Sprint 19+ earns the bolder claim)
- Sprint docs: cross-references updated
- Release notes for goja-mcp v1.0 covers the cutover + the service consolidation

### Service consolidation (Stream 2)

The tool-cap pressure: Antigravity caps ~100 tools total across all configured MCP servers. javalens-mcp surface today:

| Layer | Tool count |
|---|---|
| javalens-mcp v1.8.0 (Sprint 14) | 75 |
| v1.10.0 after modernisation (Sprint 15) | 81 (+6) |
| v1.11.0 after Fowler smell detection (was Sprint 16, now Sprint 17 under GOJA) | 99 (+18) |
| After Kerievsky (Sprint 19) | +8 |
| After SOLID (Sprint 20) | +5 |

Without consolidation, we hit the cap inside the v1.x line. Sprint 16 consolidates BEFORE Fowler/Kerievsky/SOLID add their detection content.

**Consolidation strategy** — three precedents from Sprint 11 v1.5.0:

1. **`find_pattern_usages(kind, query)`** (Sprint 11) — replaced 5 narrow `find_*` tools with one parametric tool taking a typed `kind` enum.
2. **`find_quality_issue(kind, ...)`** (Sprint 11) — replaced 8 narrow quality-check tools with one parametric tool.
3. **Forward-compatible with the eventual `find_target_candidates(catalog, kind, ...)`** from [`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md) — the unifying parametric tool over Fowler smells + Kerievsky patterns + SOLID violations + modernisations.

**Sprint 16 candidate consolidations:**

- **Refactoring tools** — `extract_method`, `extract_variable`, `extract_constant`, `extract_interface` are siblings. Candidate: `extract(kind, ...)` parametric tool. 4 → 1. **(–3 tools)**
- **Inline tools** — `inline_method`, `inline_variable`. Candidate: `inline(kind, ...)`. 2 → 1. **(–1 tool)**
- **Move tools** — `move_class`, `move_package`. Candidate: `move(kind, ...)`. 2 → 1. **(–1 tool)**
- **Hierarchy tools** — `pull_up`, `push_down`. Candidate: `move_in_hierarchy(direction, ...)`. 2 → 1. **(–1 tool)**
- **Generate tools** — 6 codegen tools (`generate_constructor`, `generate_getters_setters`, `generate_equals_hashcode`, `generate_tostring`, `override_methods`, `generate_test_skeleton`). Candidate: `generate(kind, ...)`. 6 → 1. **(–5 tools)**
- **Find call hierarchy** — `get_call_hierarchy_incoming` + `get_call_hierarchy_outgoing`. Candidate: `get_call_hierarchy(direction, ...)`. 2 → 1. **(–1 tool)**
- **Get position** — `get_method_at_position`, `get_field_at_position`, `get_type_at_position`. Candidate: `get_at_position(kind, ...)`. 3 → 1. **(–2 tools)**

**Total reduction: ~14 tools.** New surface: 81 - 14 = **67 tools** at goja-mcp v1.0. Subsequent sprints (Fowler/Kerievsky/SOLID) add detection tools that fit the new parametric pattern: `find_target_candidates(catalog="fowler_smell", kind="long_method")` etc. — adding 30+ kinds across the catalogs adds **zero** to the tool count beyond the parametric front door.

### Version line reset (Stream 3)

- Bundle-Version: `1.10.0.qualifier` (javalens) → `1.0.0.qualifier` (goja)
- Reactor pom `<version>1.10.0-SNAPSHOT</version>` → `<version>1.0.0-SNAPSHOT</version>`
- Tag: `v1.0.0` (new tag namespace; old `v1.x.x` javalens tags remain on the archived repo)
- Release notes: `docs/release-notes/v1.0.0.md` (under goja-mcp) — explains the rebrand, the consolidation, the migration story

## Repos touched

- **Fork (rebrand to `goja-mcp`)** — full code rename, version reset, repo slug change.
- **Manager (rebrand to `goja-studio` in parallel)** — package.json + Cargo.toml + tauri.conf.json rename, settings/data dir migration logic, release-poller URL update, README rewrite. Tracked under a parallel **manager Sprint 18** (after manager Sprint 17 lands the HTTP/SSE adoption — both rebrand together for a coherent v1.0 / v1.0 launch story).

## Out of scope (settled)

- **Adding new tools** — Sprint 16 is consolidation, not expansion. Fowler smells (Sprint 17), multi-step orchestration (Sprint 18), Kerievsky (Sprint 19), SOLID (Sprint 20) all add their content under the GOJA brand at goja-mcp v1.1+.
- **Networked service / multi-user** — separate vision sprint, eventually under GOJA v2.0+. See [`../../../javalens-manager/docs/sprints/sprint-future-networked-service.md`](../../../javalens-manager/docs/sprints/sprint-future-networked-service.md).
- **The target-form catalog unification** ([`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md)) — that's the eventual cross-Sprint-17-19 consolidation; it ships under GOJA later (eventually v2.0 territory).

## Authorship / attribution rule

No Claude / AI / "Generated by …" attribution anywhere — commit messages, PR bodies, release notes, code comments, docs.

## Order of work (suggested)

1. **Stage 0 — Service consolidation design.** Decide final parametric-tool surface. Document `(catalog, kind)` schema for the refactoring + codegen consolidations. Verify forward-compatibility with the target-form-catalogs framework. This stage is documentation-heavy; no code changes yet.

2. **Stage 1 — Parametric tool implementations.** Build the consolidated tools (`extract`, `inline`, `move`, `move_in_hierarchy`, `generate`, `get_call_hierarchy`, `get_at_position`). Each new parametric tool dispatches to the existing narrow tool's implementation by `kind` value. **Existing narrow tools stay functional through this sprint** — backwards compatibility for users migrating, deprecated in goja-mcp v1.1.

3. **Stage 2 — Java code rename pass.** Scripted: `find . -name '*.java' -exec sed -i 's/org\.javalens\./org.goja./g'`, plus class-rename via JavaLens's own `rename_symbol` tool (eat your own dog food). Includes: package directories, package declarations, import statements, fully-qualified type references, MANIFEST.MF Bundle-SymbolicName, pom artifactId, product file uid.

4. **Stage 3 — Artifact + runtime rename.** Jar filename `javalens.jar` → `goja.jar`. MCP service prefix `jl-` → `goja-`. Settings/cache dir paths `~/.config/javalens-manager/` → `~/.config/goja-studio/` (manager-side; coordinate with manager Sprint 18).

5. **Stage 4 — Version line reset.** Bundle-Version + pom versions go from `1.10.0` (the modernisation release line) to `1.0.0` (goja-mcp v1.0). Reactor `mvn clean verify` confirms a clean build under the new identity.

6. **Stage 5 — Repo slug rename.** `gh api repos/haraldwegner/javalens-mcp -X PATCH -f name=goja-mcp`. Push current heads to the new slug; old slug auto-redirects.

7. **Stage 6 — README + docs rewrite.** Full README rewrite for the rebrand. Update all sprint docs' cross-references. Release notes for goja-mcp v1.0.

8. **Stage 7 — Release.** Tag `v1.0.0` (under the new repo); CI publishes the GitHub Release as Latest under the goja-mcp namespace.

## Critical files

- **Service consolidation:** every Tool implementation in `org.javalens.mcp/src/org/javalens/mcp/tools/*` (will move to `org.goja.mcp/src/org/goja/mcp/tools/*` in the rename pass).
- **Brand cutover:** every `.java` source file (package + class rename), every `MANIFEST.MF` (Bundle-SymbolicName + Require-Bundle), every `pom.xml` (artifactId + version), `org.javalens.product/org.javalens.product` (product file uid + version), `README.md`, every release notes file, every sprint doc.
- **Coordination with manager (Sprint 18):** the manager's release-poller URL, deployed MCP-config writer's service prefix, settings/cache dir migration logic.

## Reusable infrastructure already in place

- **`rename_symbol` tool** — javalens-mcp's own rename tool. Eat the dog food: use it to drive the class-rename pass across all source files. (After the Sprint 14b apply-policy retrofit, `rename_symbol` auto-applies, making this a one-call refactor per class.)
- **Parametric tool precedent (Sprint 11 v1.5.0)** — `find_pattern_usages(kind, query)` and `find_quality_issue(kind, ...)` already validated the consolidation pattern. Sprint 16 extends the same pattern to refactor + codegen + analysis tool families.

## Verification (sprint exit)

- **Tool count audit:** `gh api orgs/haraldwegner/repos/goja-mcp/contents/<tool registry>` confirms <80 tools per workspace service. Headroom preserved for Sprints 17-20.
- **Backwards compatibility:** existing narrow tools (`extract_method`, `inline_variable`, etc.) still work in goja-mcp v1.0 — just deprecated in favor of the parametric tools. Migration period: goja-mcp v1.0 → v1.1, narrow tools removed at v1.2.
- **End-to-end smoke:** install goja-mcp v1.0 in a fresh manager workspace; verify health_check returns the new service ID + tool list; verify a representative refactor (e.g., `extract` with `kind: "method"`) works identically to the legacy `extract_method`.
- **Full reactor:** `mvn clean verify` GREEN under the renamed packages, IDs, and version line.

## Cut lines (if a stage hits unexpected pain)

- **Stage 1 (parametric tool implementation)** can split: ship the rebrand + version reset at goja-mcp v1.0 with the existing tool surface intact (75 tools at the v1.0 line); consolidation slips to goja-mcp v1.0.x patch. Trade-off: less headroom for Sprint 17 (Fowler smells, +18 tools) — would push Fowler to a tighter sub-set or a multi-release stage.
- **Stage 6 (README rewrite)** can ship as a polish v1.0.1 if Stage 6's writing time stretches. v1.0 ships with a minimal README that says "rebranded from javalens-mcp; full docs incoming"; full rewrite lands as v1.0.1 within a week.
- **Brand cutover is the floor — must ship.** The whole sprint exists to do the rebrand; if Stage 2 (Java rename pass) hits unexpected pain, the sprint pauses and re-plans rather than partially ships.

## Build / test commands

```bash
# Fork (under goja-mcp after rename)
cd /home/harald/CursorProjects/goja-mcp   # post-rename path
mvn clean verify
# Focused: parametric tool tests
mvn -pl org.goja.mcp.tests -am -Dtest='Extract*Test,Inline*Test,Move*Test,Generate*Test' verify

# Verify tool count is <80
java -jar org.goja.product/target/products/goja.jar -count-tools  # (new helper for the audit)
```

## Definition of Done

- [ ] All `org.javalens.*` packages renamed to `org.goja.*`. All `JavaLens*` classes renamed to `Goja*`. Full reactor `mvn clean verify` GREEN.
- [ ] All 4 OSGi bundles' `Bundle-SymbolicName` updated. All `Require-Bundle` declarations updated. All 8 reactor poms' `artifactId` updated.
- [ ] Product file (`org.goja.product/org.goja.product`) renamed and `uid` updated.
- [ ] Jar artifact renamed: `goja.jar`. MCP service prefix renamed: `goja-*`.
- [ ] Manager-side migration logic (Sprint 18, parallel): settings/cache dirs migrate `~/.config/javalens-manager/` → `~/.config/goja-studio/` on first launch under the new manager.
- [ ] Parametric tools shipped: `extract`, `inline`, `move`, `move_in_hierarchy`, `generate`, `get_call_hierarchy`, `get_at_position`. Each dispatches to the legacy narrow tool's implementation by `kind`.
- [ ] Tool count post-consolidation: <80 (confirmed via audit script).
- [ ] Repo slug renamed: `haraldwegner/javalens-mcp` → `haraldwegner/goja-mcp`. Old slug auto-redirects.
- [ ] README + all sprint docs updated with the new brand + cross-references.
- [ ] `docs/release-notes/v1.0.0.md` (under goja-mcp) written — covers the brand cutover, the consolidation, the migration story.
- [ ] Bundle-Version + pom versions reset `1.10.0.qualifier` → `1.0.0.qualifier`.
- [ ] Tag `v1.0.0` pushed under the new repo; CI publishes the GitHub Release as Latest.
- [ ] No AI-attribution boilerplate anywhere.
- [ ] Memory updated: `project_sprint_state.md` → "javalens-mcp brand retired at v1.10.0; goja-mcp v1.0 shipped; Sprint 17 (Fowler smell detection) queued under GOJA brand".
