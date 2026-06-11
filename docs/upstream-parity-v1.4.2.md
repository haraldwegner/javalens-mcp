# Upstream parity audit — pzalutski-pixel/javalens-mcp @ v1.4.2

Date: 2026-06-11 (Sprint 14b Stage A0 / Phase 0).
Auditor baseline: fork v1.8.6 (75 tools, HTTP/SSE default).

> **Retarget note:** Sprint 14b scoped this audit against upstream **v1.3.5** (latest as
> of 2026-06-07). Upstream shipped four further releases in the six days since —
> v1.3.6 (06-06), v1.4.0 (06-09), v1.4.1 (06-10), v1.4.2 (06-11) — 51 commits, 179 files.
> This audit covers through **v1.4.2**. The v1.2→v1.3.5 assessment from
> [`sprints/sprint-future-upstream-parity-audit.md`](sprints/sprint-future-upstream-parity-audit.md)
> (fine-grained `find_*` tools → covered by our parametric consolidation; compound
> analysis tools → same names already present; Bazel → unverified) remains valid and is
> not repeated here.

## USP verification (the strategic question first)

Both fork differentiators are **intact at upstream v1.4.2**:

1. **Transport.** Upstream has no transport package and a single `JavaLensApplication` —
   **stdio-only**. No HTTP, no SSE, no multi-client story. Our resident-JVM-per-workspace
   model remains structurally out of their reach without an equivalent rework.
2. **Apply + undo.** Their v1.4.1 "ASTRewrite synthesis seam" migration looked alarming in
   the commit log but is about HOW edits are computed, not what happens to them. Verified
   in source at v1.4.2:
   - `ExtractVariableTool` (local seam): *"Returns the text edits needed… The caller
     should apply these edits"* — returns `edits` maps via `TextEditConverter.toEditMaps`.
   - `PullUpTool` (new descriptor seam): returns `editsByFile` — same hand-apply contract.
   - No `PerformChangeOperation`-and-return-undo anywhere in their tool layer; no change
     cache, no preview/apply/undo tools.

   **Sprint 14b's auto-apply + undo contract remains a genuine differentiator.** Their
   synthesis seam is complementary prior art for our Stage A3 migration (edge-case
   reference), not competition.

Tool-count optics: upstream now also advertises **75 tools** (was 63 at v1.3.5). Different
surfaces — theirs grew analysis + cleanup; ours has codegen, duplicates, DI, dependency
management. Release-notes framing for v1.9.0 should stop leaning on raw tool count and
lean on the apply/undo + multi-client capabilities.

## Porting list — v1.3.5 → v1.4.2 delta

### PORT NOW (this sprint, cheap)

| Item | Upstream ref | Fork action |
|---|---|---|
| Report real server version from bundle manifest. Our `serverInfo.version` is hardcoded `"2.0.0-SNAPSHOT"` (`McpProtocolHandler.java:195`) while the fork is at 1.8.6 — live clients see a wrong version today. | `2a61571` (v1.4.2) | Read `Bundle-Version` via `FrameworkUtil.getBundle(...).getVersion()` with a safe fallback; assert in `McpProtocolHandlerTest`. **Cherry-picked in Stage A0.** |

### NOT APPLICABLE (their infra, not ours)

- `JreInstallEnsurer` default-VM fix (v1.3.6) — class doesn't exist in the fork (their
  npm-launcher install path; we distribute via the manager).
- npm README/docs updates, GitHub issue templates (v1.3.6) — distribution-channel docs.

### DEFER — assigned to existing roadmap slots

| Item | Upstream refs | Where it lands | Rationale |
|---|---|---|---|
| `apply_cleanup` (headless JDT clean-ups, 10-item catalog) + `diagnose_and_fix` | `e0f13af`, `d131b1d`, `a3c1e1e` | **Sprint 15 (v1.10.0)** evaluation | Overlaps our `apply_quick_fix` + the modernisation sweeps; decide there to avoid duplicate surface. Their catalog approach is a good fit for our parametric-tool pattern. |
| `find_unreachable_code`, `find_affected_tests`, `analyze_change_impact` transitive mode, data-flow `followCalls` (interprocedural), JPA + HTTP framework extractors, `ProjectGraphService` | v1.4.2 items 1–6 | **Sprint 15 / Sprint 17** under cap budget | Real analysis value (`find_affected_tests` pairs with our test story). Antigravity ~100-tool cap: we hit 79 after 14b — porting these pushes past 85; sequence AFTER Sprint 16 consolidation where possible. |
| 3 structural refactorings we lack: `extract_superclass`, `introduce_parameter_object`, `move_type_to_new_file` | `0c66095`, `e80b451`, `e76e2a3` | **Sprint 19 (Kerievsky)** for introduce_parameter_object; **Sprint 17+** for the other two | They're Fowler/Kerievsky-catalog refactorings — port them THROUGH our apply-contract base class (they'd be the first post-14b tools subject to the PR gate). |
| Lombok agent bundling + generated-member analysis | `eaa92cc`, `c1c9123` | **Sprint 15 (v1.10.0)** candidate — promote if a JATS/ORB workspace needs it sooner | High practical value for Lombok codebases; ~1–2 d incl. agent-jar redistribution check (Lombok is MIT—fine). |
| Java 25 platform line: Eclipse 2025-12 target, compact source files (implicit classes), module imports, flexible constructor bodies, markdown `///` Javadoc | `2119dd8` + v1.4.0 items 2–4, `afc0cf8` | **Dedicated target-platform bump**, sensibly at **Sprint 16 (GOJA cutover)** where the target file is already being touched | Their largest block (~20 commits). Target-platform churn is OSGi-risky; bundling it with the rebrand gives one coordinated breaking window. Until then the fork analyzes ≤ Java 21 sources correctly — acceptable for current users (JATS is on 21). |

### SKIP (already covered — re-confirmed from the v1.3.5 audit)

- Fine-grained reference tools (`find_annotation_usages`, `find_casts`, `find_instanceof_checks`, …) — our parametric `find_pattern_usages(kind)` / `find_quality_issue(kind)` cover these (Sprint 11 consolidation; deliberate cap-pressure decision).
- Compound analysis tools (`analyze_file/type/method`, `get_type_usage_summary`) — present since our v1.7.0.

## Standing risk

Upstream velocity is high (4 releases/6 days). Re-run this audit at every fork release
boundary (add to the release-stage checklist) rather than per-sprint — the porting-list
format above is the template.
