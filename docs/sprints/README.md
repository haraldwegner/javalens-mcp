# JavaLens MCP — Sprint docs index

Team collaboration convention:

> We work agile in sprints. **Sprint docs** (in each repo under `docs/sprints/...`)
> are requirements + design + architecture. **Actionable plans** are sprint-doc
> derivatives that an agent can execute; they live outside the repo (under
> `~/.claude/plans/`). Do NOT conflate the two.

This directory holds the fork's sprint docs and scaffold plans. Closed sprints flip status in `docs/bugs.md` + `docs/upgrade-checklist.md` + ship release notes under `docs/release-notes/`.

## Active

- [`sprint-14-backlog.md`](sprint-14-backlog.md) — **v1.8.0** target (in flight). Lifecycle hygiene + agent-trust restoration. Phase A: 9 bug fixes. Phase B: `refresh_workspace`, FQN-based `find_*`, `find_duplicate_code`. Phase C: Gradle path for Ring 3 dep tools. Phase D: fork-side doc-scrub + README marketing pivot + cutover.

## Next (immediately after v1.8.0)

- [`sprint-14b-refactoring-full-apply.md`](sprint-14b-refactoring-full-apply.md) — **v1.9.0** (SHIPPED). Uniform auto-apply-by-default + undo handle for every refactoring tool. Closes the gap where 10 of 15 local refactor tools today return text-edit descriptions and force the agent to hand-apply. Opt-in `auto_apply: false` for staged-refactor / impact-analysis workflows. **Ships `replace_duplicates(cloneGroupId)`** so the Sprint 14 B.3 `find_duplicate_code` workflow closes in v1.9.0. **~10-11 days** — all plumbing on top of JDT-LTK's existing apply + undo engine. Establishes the durable **refactoring tool contract** (apply path mandatory for every new refactor tool going forward).

## Future (scaffolds — theme + summary, not actionable plans)

Sequencing is suggestive, not committed. Each scaffold names its dependencies; some can ship in any order.

| Scaffold | Theme | Depends on |
|---|---|---|
| [`sprint-15-modernisation-sweeps.md`](sprint-15-modernisation-sweeps.md) | Find-and-batch-apply for `var` / records / sealed types / pattern-matching switch. The **final javalens-branded release (v1.10.0)**; also carries the Cursor-DX block + bug #1/#3. | Builds on 14b apply/undo |
| [`sprint-16-goja-rebrand.md`](sprint-16-goja-rebrand.md) | GOJA rebrand + parametric tool consolidation. Closes the javalens-mcp brand at v1.10.0 and resets the line to **goja-mcp v1.0**. | Sprint 15 |
| [`sprint-17-fowler-smell-detection.md`](sprint-17-fowler-smell-detection.md) | 18-tool Fowler smell catalog. First big content release under GOJA. Highest single-leverage gap. | Independent (post-rebrand) |
| [`sprint-18-multi-step-orchestration.md`](sprint-18-multi-step-orchestration.md) | `plan_refactoring` + `apply_refactoring_plan` + rollback — the multi-step framework gate (e.g. the `safe_refactor` test-gated loop). | 14b Change cache + Sprint 17 smells |
| [`sprint-19-kerievsky-refactoring-to-patterns.md`](sprint-19-kerievsky-refactoring-to-patterns.md) | Pattern-targeted multi-step refactorings (toward + away from patterns). | Sprint 18 framework |
| [`sprint-20-solid-detection.md`](sprint-20-solid-detection.md) | 5-principle violation detection (SRP, OCP, LSP, ISP, DIP). | Sprint 17 smell infrastructure |
| [`sprint-future-android-readonly.md`](sprint-future-android-readonly.md) | Manifest / resource / layout / lifecycle / binding read-only tools | Sprint 14 Gradle path (shipped v1.8.0 Phase C) |
| [`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md) | Parametric `find_target_candidates(catalog, kind)` consolidation | Sprints 15-20 ship the detection content first |

*(Shipped, removed from this table: HTTP/SSE transport → v1.8.5 ([`sprint-14a-http-sse-transport.md`](sprint-14a-http-sse-transport.md)); refactoring full-apply → v1.9.0 ([`sprint-14b-refactoring-full-apply.md`](sprint-14b-refactoring-full-apply.md)); upstream-parity audit → ran in 14b against v1.4.2 ([`../upstream-parity-v1.4.2.md`](../upstream-parity-v1.4.2.md)).)*

## Shipped

Sprint history before v1.7.1 was tracked without per-sprint docs — earlier fork sprints (10 → 13, fork v1.3.0 → v1.7.0) live in [`../release-notes/`](../release-notes/) only. v1.7.1 onward, the sprint backlog is here.

| Fork release | Sprint | Theme |
|---|---|---|
| v1.7.1 (May 2026) | Sprint 13 cleanup | 5 bug fixes (#1–#5 from `docs/bugs.md`) |
| v1.7.0 (Apr 2026) | Sprint 13 | Code generation (Ring 2) + dep mgmt (Ring 3) + workflow polish (Ring 4) — +11 tools |
| v1.6.0 (Mar 2026) | Sprint 12 | Workspace verification — `compile_workspace` + `run_tests` |
| v1.5.x (Feb 2026) | Sprint 11 | LTK structural refactorings + parametric tool consolidation |
| v1.4.0 (Jan 2026) | Sprint 10 | `WorkspaceFileWatcher` (live workspace.json reconciliation) |
| v1.3.0 (Dec 2025) | Sprint 9 | Multi-project `WorkspaceManager` (workspace-first design) |

See [`../release-notes/`](../release-notes/) for per-release detail.

## How a scaffold becomes an actionable plan

When a scaffold's sprint is next to ship: rename to `sprint-N-backlog.md`, expand to the actionable-plan structure from the user's `~/CLAUDE.md` collaboration spec (Overview / Goals / Stage −1 Prerequisites / Stages 0…N with checkpoints / Risk + mitigation / Quality gates / Final sign-off). The scaffolds above are the *theme + candidate-item list* foundation that the actionable plan grows from.

## Source of long-form planning rationale

Manager's [`../../../javalens-manager/docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) has the long-form rationale for the future scaffolds — "Strategic discussion: toward Claude as the best Java dev" sections. The scaffolds in this directory are short-form derivatives of that document plus 2026-06-04 fork-Sprint-14 planning Qs.

## Numbering note

The fork now has its own sprint sequence (Sprint 14 onward), independent of the manager's `vN.x` releases. Earlier fork work was tracked by release version (`v1.5.0`, `v1.6.0`, …); v1.7.1 was Sprint 13 cleanup; **v1.8.0 is Sprint 14** and is the first fork release with a full sprint backlog doc in this directory.
