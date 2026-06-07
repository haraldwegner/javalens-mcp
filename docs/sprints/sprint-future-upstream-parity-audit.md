# Fork Sprint (SCAFFOLD — FOLDED) — Upstream-parity audit (vs pzalutski-pixel/javalens-mcp v1.3.5)

> **Status: FOLDED into Sprint 14b on 2026-06-07.** This was originally planned as Phase 0 of Sprint 15 (Fowler smell detection). After the 2026-06-07 fork-roadmap reordering (HTTP/SSE promoted to Sprint 14a, full-apply policy retrofit re-sequenced to Sprint 14b, Modernisation pulled forward to Sprint 15, Fowler pushed to Sprint 17 under the GOJA brand), the upstream-parity audit moved to **[`sprint-14b-refactoring-full-apply.md`](sprint-14b-refactoring-full-apply.md) → v1.9.0** as its Phase 0. This file is retained as a reference document for the audit's scope and findings checklist.

**Surfaced 2026-06-04** in fork Sprint 14 planning: upstream `pzalutski-pixel/javalens-mcp` moved from v1.2.0 (April) to **v1.3.5** (May 26), 63 tools. We want to confirm we haven't drifted past anything they shipped that would help us.

**Sprint placement (post-folding):** Phase 0 of [`sprint-14b-refactoring-full-apply.md`](sprint-14b-refactoring-full-apply.md). The audit + porting-list step is 1-2 days; cheap cherry-picks fold into Sprint 14b's v1.9.0; bigger items (Bazel rework, annotation-processor handling) defer to a later sprint if they surface. The apply-policy retrofit is the sprint's main work and is independent of audit outcome.

## Theme

This is a **research sprint**, not a feature sprint. Clone the upstream at v1.3.5, diff against our pre-fork point (around their v1.2.0 / our pre-Sprint-9 base), categorize what they added, decide what to port vs skip.

## What we already know (from 2026-06-04 README + releases page check)

Upstream additions since v1.2 vs our coverage:

| Upstream addition | Our coverage |
|---|---|
| 9 fine-grained reference tools (`find_annotation_usages`, `find_type_instantiations`, `find_casts`, `find_instanceof_checks`, `find_reflection_usage`, …) | **Already covered** via parametric `find_pattern_usages(kind)` + `find_quality_issue(kind=reflection)` since Sprint 11 v1.5.0. We deliberately consolidated to fit Antigravity's ~100-tool cap. |
| 4 compound analysis tools (`analyze_file`, `analyze_type`, `analyze_method`, `get_type_usage_summary`) | **Already covered** (same names, present in our v1.7.0). |
| Bazel build-system support | **Listed in our README's build-system table** but needs verification — README line may be aspirational. Phase 0 audit confirms. |
| Comprehensive "generated sources" + "annotation processor" handling across Maven, Gradle, Bazel | **Partial.** Maven via classpath; PDE via `.classpath`; Gradle Tooling API. Annotation-processor source roots may have gaps worth auditing. |
| AI training-bias warning section in README | **Already present** in our README (Sprint 11-era addition). |
| Refactoring preview→apply gate (Eclipse-IDE-style) | **Not present in either repo.** This is our differentiator opportunity — see [`sprint-future-refactoring-full-apply.md`](sprint-future-refactoring-full-apply.md). Note for the audit: confirm upstream doesn't have it either by inspecting their refactor tool implementations. |

## Candidate items

- **(a) Diff audit** — clone upstream v1.3.5, diff their `src/` against ours at the divergence point. Categorize every new file/method into: already-covered / worth-porting / skip-and-document. Output: a "porting list" document.
- **(b) Bazel detection + classpath** — add Bazel as a fourth build system in our detection matrix. Probably 3-5 days; requires understanding Bazel's `query` interface and how to extract per-target classpaths.
- **(c) Annotation-processor source-root handling** — audit our Maven / Gradle / PDE / Tycho paths for whether generated sources end up on the JDT classpath uniformly. Probably 1-2 days; specific to each build-system path.
- **(d) Small fixes worth picking up** — anything else the diff reveals (bug fixes in tools that overlap with ours; small performance wins; schema improvements).
- **(e) Document the divergence publicly** — section in README explaining what we have that upstream doesn't (manager, multi-project workspace, LTK refactorings, code generation, etc.) and what upstream has that we don't (Bazel, until we port it).

## Dependencies

- **Independent of all other sprints.** Touches no in-flight work; produces a porting plan + (potentially) Bazel detection.
- Should run before any sprint that touches build-system detection (Sprints 15-19 don't; this is parallel-safe).

## Acceptance signal

- Audit document landed (under `docs/`, summarising diff results).
- If (b) shipped: Bazel project loads and basic navigation works (search_symbols + find_references).
- If (c) shipped: `compile_workspace` on a project with annotation processors picks up generated sources without manual config.
- README has a "Relation to upstream" section that's clearer than today's "fork" framing (paired with [Q5 README marketing pivot](../../README.md) work).

## Source planning notes

- 2026-06-04 fork Sprint 14 planning (Q3 review).
- Upstream URL: `pzalutski-pixel/javalens-mcp` on GitHub.
- Fork divergence point: our v1.3.0 (Sprint 9 era).
- Upstream releases: v1.3.0 (May 9) through v1.3.5 (May 26) — no per-release notes posted; the README highlights are the only summary.
