# Fork Sprint 17 (SCAFFOLD) — Multi-step refactoring orchestration

**Status:** scaffold only. Theme + summary + candidate items. Not an actionable plan yet.

## Theme

The framework gate for complex refactorings. Fowler's primitives are individually compileable but most interesting moves (Replace Conditional with Strategy, Move Accumulation to Visitor, Modernise All Switches in a Module) are a *sequence* of primitives with checkpoint-validation between steps. Today the agent micromanages step-by-step; an orchestrated framework lets a single tool call drive the full sequence with rollback on failure.

This sprint is **framework infrastructure**, not domain tools. Sprint 18 (Kerievsky), Sprint 19 (SOLID), Sprint 16 (modernisation batch-apply) all benefit when this lands.

## Candidate items

- **`plan_refactoring(target, kind)`** — returns a list of `{step, primitive_tool_call, expected_state_after, rollback_to}` records. The kind enum bootstraps with ~3 simple cases (Compose Method, Replace Type Code with Class, Inline Singleton) and grows in Sprint 18.
- **`apply_refactoring_plan(planId, options)`** — walks the plan, calls primitive tools in sequence, runs `compile_workspace` + optional `run_tests` between steps when `options.validate_each=true`, rolls back to last good state on failure.
- **`inspect_refactoring_state(planId)`** — returns the diff applied so far, which steps ran, which are pending. The agent uses this between calls to decide continue / modify / abort.
- **`undo_refactoring_plan(planId)`** — explicit rollback of an in-flight plan; also usable post-success if smoke testing surfaces issues.
- **In-memory plan store** — session-scoped (in-process, no persistence), TTL ~1 hour, stacked LTK `Change.getUndo()` per step.

## Rollback semantics (the hard part)

A failed step at index N requires undoing steps N-1, N-2, … back to pre-refactor state. JDT-LTK's `Change` API has `getUndo()` per Change; stack them. Validation each step needs to detect both compile failures and semantic regressions (the latter via `run_tests` when the user opts in).

## Dependencies

- **Builds on Sprint 14a's apply primitives** — the `Change` cache, `apply_refactoring`, `undo_refactoring`, and `inspect_refactoring` tools shipped in 14a (v1.9.0) are the foundation. Sprint 17's `apply_refactoring_plan` is thin glue that calls per-step refactor tools with `auto_apply: false`, stages the resulting changeIds, commits them atomically (or rolls back). Without 14a, this sprint would have to ship the apply layer itself.
- **Independent of Sprint 14 and 15 content** beyond the 14a foundation.
- **Gates** Sprint 18 (Kerievsky pattern recipes need orchestration to execute).
- Reuses Sprint 11 `AbstractRefactoringTool` LTK plumbing (via 14a's retrofit of all 15 refactor tools).

## Acceptance signal

- 4 new tools shipped. `health_check` +4.
- End-to-end smoke: agent calls `plan_refactoring(target=method, kind="compose_method")` → gets plan → `apply_refactoring_plan(planId)` → resulting code compiles + tests stay green.
- Forced-failure smoke: inject a syntax error after step 2 of 4 → `apply_refactoring_plan` detects via `compile_workspace`, rolls back to pre-refactor state.

## Source planning notes

- Manager [`docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) — "Multi-step MCP tools — feasibility" section.
- Related: [`sprint-future-refactoring-full-apply.md`](sprint-future-refactoring-full-apply.md) (the simpler preview→apply flow for single-step refactorings). That can ship first and provide the per-step change-cache that orchestration builds on top of.
