# Sprint 14b — Refactoring auto-apply + undo + upstream pzalutski v1.3.5 sync

> **Status: re-sequenced 2026-06-07.** Originally drafted as Sprint 14a. Re-sequenced to **Sprint 14b** after Sprint 14a was reassigned to HTTP/SSE transport (the architectural fix for manager bug #9's 30 GB JVM leak + the EXECSIM sandbox unblock + multi-client lock contention). This sprint absorbed the **upstream pzalutski v1.3.5 parity-audit sync** (previously [`sprint-future-upstream-parity-audit.md`](sprint-future-upstream-parity-audit.md), originally scoped as Sprint 15 Phase 0) into its scope — porting the upstream parity diff is small enough to bundle with the apply-policy retrofit.
>
> **Target version: javalens-mcp v1.9.0** (minor bump because every refactor tool's return shape changes — `{ filesModified, diff, undoChangeId }` instead of text-edit descriptions — that's a contract break).
>
> **Predecessor:** [`sprint-14a-http-sse-transport.md`](sprint-14a-http-sse-transport.md) → v1.8.5.
>
> **Successor:** [`sprint-15-modernisation-sweeps.md`](sprint-15-modernisation-sweeps.md) → v1.10.0.

**Estimated duration:** ~12-13 days (~9 days apply-policy retrofit + ~1-2 days for the `replace_duplicates` companion workflow + ~1-2 days for the upstream parity audit and cherry-picks).

**Surfaced 2026-06-04** during fork Sprint 14 planning ("How can we make it perform the full refactoring?"). **Promoted to Sprint 14a then re-sequenced to Sprint 14b on 2026-06-07** after HTTP/SSE transport claimed the 14a slot.

**Policy attached:** every refactoring tool added to the fork from this sprint onward MUST ship with the apply path. See "Refactoring tool contract" section below — this becomes a durable PR-review gate.

## Folded-in scope: `readOnlyHint` tool annotations (2026-06-11)

Originally penciled as a standalone v1.8.7 hotfix on the 14a line; rollout pushed back and
folded here instead. Scope: `tools/list` gains `annotations: { readOnlyHint: true }` for
the detect tool set (`find_*`, `get_*`, `analyze_*`, `search_*`, `list_projects`,
`health_check`, `validate_syntax`); mutating tools ship without it. Motivation: Cursor Ask
mode rejects `CallMcpTool` as non-readonly ([`../javalens_feeback_from_cursor.md`](../javalens_feeback_from_cursor.md));
the MCP spec ≥ 2025-03-26 annotation lets clients allow read-only tools in restricted
modes. Acceptance: Cursor Ask mode runs `health_check` / `analyze_type` / `find_references`
without switching to Agent mode. Full design record in
[`sprint-14a-http-sse-transport.md`](sprint-14a-http-sse-transport.md) § v1.8.7.

## Phase 0 — Upstream parity sync (folded in 2026-06-07; **retargeted v1.3.5 → v1.4.2 on 2026-06-11**)

Originally [`sprint-future-upstream-parity-audit.md`](sprint-future-upstream-parity-audit.md), targeting upstream v1.3.5 (May 26 2026, 63 tools). At audit time upstream had already shipped v1.3.6/v1.4.0/v1.4.1/v1.4.2 (75 tools, 2026-06-11) — the audit covers through **v1.4.2**; findings + porting list at [`../upstream-parity-v1.4.2.md`](../upstream-parity-v1.4.2.md). Key outcome: both USPs verified intact (upstream is still stdio-only and still returns hand-apply `editsByFile` from ALL refactor tools, including their new v1.4.1 descriptor-based structural ones). One cherry-pick landed (manifest-derived `serverInfo.version`). Bigger items (Java 25 target platform, Lombok agent, analysis tools) assigned to later sprints in the audit doc.

**Outcome:** porting list document + any cheap cherry-picks (small fixes, schema improvements) landed in v1.9.0. **No coupling between upstream-audit outcome and the apply-policy retrofit** — both proceed independently within the sprint.

## Scope: what counts as a refactor tool

The policy applies to **refactor tools** (tools that mutate code) and NOT to **detect tools** (tools that return data).

- **Detect tools** — `find_*`, `analyze_*`, `get_*`, `search_*`. Return data describing the codebase. No mutation. **Not subject to the apply policy.** `find_duplicate_code` (Sprint 14 B.3), `find_references`, `analyze_type`, `get_call_hierarchy_*` etc. all stay as-is.
- **Refactor tools** — anything that mutates source. The current 15-tool set: 10 local (`rename_symbol`, `extract_method`, `extract_variable`, `extract_constant`, `extract_interface`, `inline_method`, `inline_variable`, `change_method_signature`, `organize_imports`, `convert_anonymous_to_lambda`) + 5 structural (`move_class`, `move_package`, `pull_up`, `push_down`, `encapsulate_field`). **All 15 retrofitted in this sprint.**
- **Codegen tools** — `generate_constructor`, `generate_getters_setters`, `generate_equals_hashcode`, `generate_tostring`, `override_methods`, `generate_test_skeleton` (Sprint 13 Ring 2). **Mutate source → subject to the policy.** Audit during Stage 0; retrofit alongside the 15 refactor tools if their current implementation already uses `ASTRewrite.apply()` (likely — they probably already auto-apply); otherwise full retrofit.
- **New composite workflow tool shipped THIS sprint** — `replace_duplicates(cloneGroupId, options)` companion to `find_duplicate_code` (Sprint 14 B.3). Closes the duplicate-detection-to-removal loop. Takes a clone group, picks the canonical instance, runs `extract_method` on it, replaces each other clone's body with a call to the extracted method. All steps internally use the per-step apply primitives shipped earlier in this sprint. Detection (B.3) ships in v1.8.0; the replacement workflow ships in 14a so the loop closes in v1.9.0.

## Theme

Today's refactoring tools split into **two incompatible modes**, neither of which matches the Eclipse IDE preview→apply flow that humans use:

- **Local refactorings** (`rename_symbol`, `extract_method`, `extract_variable`, `extract_constant`, `extract_interface`, `inline_method`, `inline_variable`, `change_method_signature`, `organize_imports`, `convert_anonymous_to_lambda` — 10 tools, extending `AbstractTool`) — **return text-edit descriptions** and expect the agent to apply them via separate `Edit` calls. `RenameSymbolTool`'s class javadoc literally says: *"Returns text edits for all occurrences that need to be changed. The caller should apply these edits to perform the rename."* Error-prone for complex multi-file edits; agent has to re-implement the apply path for every refactoring.
- **Structural refactorings** (`move_class`, `move_package`, `pull_up`, `push_down`, `encapsulate_field` — 5 tools, extending `AbstractRefactoringTool`) — **auto-apply** via `PerformChangeOperation` on the JDT-LTK Change. No preview gate; the agent can't see the diff before files change.

Eclipse IDE's flow is **preview → simulate → agree → apply** — that's designed for humans who want to review every change. **The agent equivalent is different**: the agent doesn't need (and shouldn't have) a human-style approval gate on every refactor. The agent's loop is:

> refactor → verify via `compile_workspace` + `run_tests` → if green, kept; if red, `undo_refactoring(undoChangeId)`.

The "simulate" step is the agent's own correctness-check, not a user-facing approval ask. The user gave the instruction "rename X to Y"; the agent does the rename, verifies it, and either keeps it or reverts — the user isn't asked to approve every diff (that's friction, not safety).

This sprint ships that flow uniformly: **every refactor tool auto-applies by default**, returning `{ filesModified, diff, undoChangeId }` in one round-trip. The agent can OPT IN to a preview-without-apply mode (`auto_apply: false`) when its own workflow needs to stage multiple refactors atomically or inspect a complex multi-file diff before committing — but that's the agent's tactical choice, not a fixed gate.

**Competitive advantage**: upstream `pzalutski-pixel/javalens-mcp` v1.3.5 has neither LTK structural refactorings nor an undo / preview surface (their README highlights focus on analysis tools, no refactoring mentions). Shipping this is genuinely a differentiator, not catchup. Plus it closes a real correctness gap: today's 10 local refactoring tools return text-edit descriptions and force the agent to re-implement the apply path for each one.

## Design

**Auto-apply by default. Undo handle for safety. Opt-in preview-without-apply when the agent's workflow needs it.**

### Default flow (the common case — 95% of calls)

Every refactor tool gains a uniform contract: builds the Change, applies it via `PerformChangeOperation`, stores the resulting undo-Change in a session-scoped cache, returns:

```
{ filesModified: [...], diff: "...", undoChangeId: "uuid", summary: "..." }
```

The diff is included so the agent can inspect what just happened (and reason about whether to verify further); the `undoChangeId` is the safety net. Agent's loop:

```
1. <refactor tool call>          → { filesModified, diff, undoChangeId }
2. compile_workspace             → OK or errors
3. (optional) run_tests          → pass or fail
4. if (2) OR (3) failed:
       undo_refactoring(undoChangeId)
5. else: kept.
```

The user is not in this loop. They asked the agent to do X; the agent did X and verified X; they see the result.

### Opt-in preview mode (`auto_apply: false`)

Every refactor tool accepts `auto_apply: bool` (default `true`). When `false`, the tool caches the Change but does NOT perform it; returns `{ changeId, diff, summary }` only. The agent uses this when its own workflow needs preview-without-apply:

- Staging multiple refactors to apply atomically (build N changes, then apply in a transaction via Sprint 17's orchestrator).
- Inspecting a complex multi-file diff before committing (e.g. a `move_class` that touches 40+ files — the agent wants to sanity-check what the rewrite affected).
- Impact analysis (caller asked "what would this rename affect?" — agent calls `auto_apply: false`, returns the diff to the user, awaits direction).

When `auto_apply: false` was used: `apply_refactoring(changeId)` commits the cached Change; `inspect_refactoring(changeId)` returns the diff without committing.

### Tools shipped

- **`apply_refactoring(changeId)`** — commits a previously-cached Change (the result of an `auto_apply: false` tool call). Returns `{ filesModified, undoChangeId }`.
- **`undo_refactoring(undoChangeId)`** — reverses a previously-applied refactor. Returns `{ filesModified }` (the restored set).
- **`inspect_refactoring(changeId)`** — returns the cached diff without applying. Useful between `auto_apply: false` and `apply_refactoring`.
- Cache: session-scoped `Map<UUID, Change>` plus a parallel `Map<UUID, undoChange>` for applied changes. TTL ~1 hour or capacity-capped (LRU evict).

### Unifying the two current modes

The same cache + apply path handles BOTH today's text-edit-returning local refactorings AND today's auto-applying structural refactorings:

- **Local refactorings (10 tools)**: today they build a `TextEdit` collection and serialize it for the agent to apply via `Edit` calls — error-prone, error-prone to revert. Migration: wrap the `TextEdit` in a `TextChange` (or `CompositeChange` for multi-file refactorings), perform it (since `auto_apply: true` is the default), return the diff + undo handle. **Strict improvement**: the agent no longer has to hand-apply edits; reverts are one tool call instead of N file rewrites.
- **Structural refactorings (5 tools)**: today they call `PerformChangeOperation` and return a text summary; no undo handle. Migration: same apply path, but capture the undo-Change from `Change.perform()` and cache it. **Pure addition**: the auto-apply behavior is unchanged; the agent gains an undo handle for free.

JDT-LTK already has the machinery: `Change.perform()` returns the undo-Change directly; we just stash it.

### Build vs wrap — what we develop vs what JDT gives us

**100% of the apply + undo engine is in JDT-LTK already.** This sprint is a thin orchestration layer on top, not a refactoring engine in itself.

What JDT-LTK already provides (no work):

- `Change.perform(IProgressMonitor) → Change` — executes the refactor AND returns the undo-Change. The undo-Change is just another `Change`; calling `.perform()` on it reverses the operation. Symmetric API.
- `PerformChangeOperation` — workspace-level wrapper (acquires workspace lock, validates, dispatches events). Our `AbstractRefactoringTool` already uses it for the 5 structural refactorings.
- `TextChange` / `TextFileChange` / `CompositeChange` — adapters that turn raw `TextEdit` collections (what our local refactor tools already build today) into `Change` objects. So the path "I have TextEdits, I want to apply them with undo support" is one wrap call away.
- `TextEdit.apply(document, TextEdit.CREATE_UNDO) → UndoEdit` — the non-Change variant, also gives back an undo handle.

What we build (the orchestration layer):

| Component | Effort | Mechanism |
|---|---|---|
| Session-scoped `Map<UUID, Change>` + `Map<UUID, undoChange>` cache with TTL/LRU | ~0.5 day | Plain Java, in-process |
| 3 new MCP tools (`apply_refactoring`, `undo_refactoring`, `inspect_refactoring`) | ~1 day | Each ~30 lines: cache lookup + `Change.perform()` + cache-the-result |
| Migrate 10 local refactorings: wrap `TextEdit` output in a `TextChange` and `perform()` instead of serializing | ~3 days | ~0.3 day/tool. Mostly plumbing the diff into the response shape |
| Migrate 5 structural refactorings: capture the undo-Change returned by `Change.perform()` (currently discarded) | ~1 day | The `perform()` call is already there; we just stop throwing away its return value |
| Unified-diff serialization | ~1 day | **Correction 2026-06-11: built new.** Planning exploration found NO existing diff utility anywhere in the codebase (the "reusable from Sprint 13" assumption was wrong — tools return raw edit JSON, never diffs) |
| `auto_apply: bool` schema additions to all 15 refactor tools | ~0.5 day | Schema edit + handler branch |
| `replace_duplicates(cloneGroupId, options)` companion tool | ~1.5 days | New tool: takes clone-group from `find_duplicate_code`, picks canonical, calls `extract_method` + per-clone replace using the apply primitives shipped earlier this sprint. |
| Tests — new tool smokes + per-existing-tool round-trip + verify-then-undo loop + replace_duplicates round-trip | ~2 days | |
| Docs / release notes | ~0.5 day | |

**Total: ~10-11 days.** Fits one sprint comfortably. Almost all of it is plumbing on top of JDT-LTK's apply + undo engine. Strong cost/benefit case: small effort, large UX + correctness payoff, foundation for Sprint 17 orchestration ships as a side effect, AND the `find_duplicate_code` → `replace_duplicates` workflow closes in v1.9.0.

## Candidate items

- **`apply_refactoring(changeId)`** — new MCP tool. Performs the cached Change. Returns modified-file list + undo handle.
- **`undo_refactoring(undoChangeId)`** — new MCP tool. Performs the cached undo-Change.
- **`inspect_refactoring(changeId)`** — new MCP tool. Returns the diff without performing. Useful when the agent wants to re-examine a staged change.
- **`replace_duplicates(cloneGroupId, options)`** — new composite MCP tool. Closes the loop from Sprint 14 B.3 `find_duplicate_code`. Takes a clone-group ID, picks the canonical instance (or one named via `options.canonical`), extracts it as a method, replaces every other clone with a call to the new method. Implementation is thin glue over the apply primitives: extract_method on canonical (auto_apply true) → per-clone replace-body-with-call (auto_apply true) → if any step fails, undo all preceding steps via captured `undoChangeId`s.
- **In-memory Change cache** — session-scoped, TTL-evicted, capacity-capped. Single shared instance under `IJdtService` or sibling.
- **Refactoring tool refactor** — every existing refactoring tool gains the new contract: auto-apply by default returning `{ filesModified, diff, undoChangeId, summary }`; opt-in `auto_apply: false` returns `{ changeId, diff, summary }` for staged-then-applied flows.

## Migration / compatibility

Auto-apply default matches the existing structural-refactoring behavior, so there's effectively **no break** for that path:

- **Structural refactorings (5 tools, e.g. `move_class`)**: today auto-apply; tomorrow auto-apply + return undo handle. Pure addition. Old callers unaffected.
- **Local refactorings (10 tools, e.g. `rename_symbol`)**: today return text-edit descriptions for the agent to hand-apply; tomorrow auto-apply + return diff + undo handle. **Strict UX improvement** — the agent's hand-apply step disappears. Callers that previously hand-applied the returned text edits stop doing that; if a caller failed to update for the new contract, the refactor still happened (one round-trip instead of two). No correctness regression; the worst case is a redundant second apply attempt by an un-updated caller, which is a no-op against an idempotent rename.

A small set of advanced flows — multi-refactor staging, big-diff inspection, impact analysis — opt INTO `auto_apply: false`. These are tactical choices the agent makes; not user-facing.

**Permission model (separate from API):** if the agent is running in a "ask-before-acting" mode (CLAUDE Code's permission system or similar), the agent's own framework handles the approval prompt before calling the refactor tool. That's an agent-runtime concern, not a tool-API concern. The MCP tool itself just does what it's told.

## Refactoring tool contract (durable policy, effective v1.9.0)

**Every new refactoring tool added to the fork from v1.9.0 onward MUST implement the apply path.** PR-review gate.

The contract:

1. Tool builds a JDT `Change` (`TextChange` / `TextFileChange` / `CompositeChange` for text-edit-based refactorings; LTK `RefactoringChange` for structural).
2. By default (`auto_apply: true`): tool calls `Change.perform()`, captures the returned undo-Change, caches it under a fresh `undoChangeId`, returns `{ filesModified, diff, undoChangeId, summary }`.
3. When called with `auto_apply: false`: tool caches the un-performed Change under a `changeId`, returns `{ changeId, diff, summary }`. A subsequent `apply_refactoring(changeId)` performs it.
4. NEVER return raw `TextEdit` collections expecting the agent to hand-apply. The Sprint 14a migration ELIMINATES this pattern; new tools must not reintroduce it.

PR-review checklist (added to fork's contributor docs as part of this sprint):

- [ ] Does the new refactoring tool extend `AbstractApplyingRefactoringTool` (new base class shipped 14a) or implement the same contract?
- [ ] Does the tool return `{ filesModified | changeId, diff, undoChangeId?, summary }`?
- [ ] Does the tool accept `auto_apply: bool` and honour it?
- [ ] Do the tool's tests include BOTH a happy-path apply-and-verify-via-compile AND a forced-fail-then-undo round-trip?
- [ ] If the tool is composite / workflow (like `replace_duplicates`), does it use per-step apply primitives + rollback all preceding steps on failure?

Tools NOT subject to this policy (re-stated from Scope section above): `find_*`, `analyze_*`, `get_*`, `search_*` — anything that returns data without mutating source.

## Dependencies

- **Sprint 14 (v1.8.0) ships first**; Sprint 14a (v1.9.0) follows immediately.
- **Audits Sprint 13 codegen tools** (Ring 2 — generate_constructor etc.) during Stage 0: if they already auto-apply via `ASTRewrite.apply()` (likely), retrofit to capture undo handle. If they return TextEdit collections, full retrofit.
- **Closes the find_duplicate_code workflow** — Sprint 14 B.3 ships detection in v1.8.0; this sprint's `replace_duplicates` composite ships the removal step in v1.9.0. The loop closes one release cycle after detection.
- **Enables Sprint 17 multi-step orchestration** — the in-memory Change cache + `apply_refactoring` / `inspect_refactoring` / `undo_refactoring` primitives are exactly what `apply_refactoring_plan` needs. Sprint 17 becomes thin glue on top.

## Acceptance signal

- 3 new tools (`apply_refactoring`, `undo_refactoring`, `inspect_refactoring`). `health_check` +3.
- Every existing refactoring tool's contract documented as auto-apply-by-default (with `auto_apply: false` opt-in for preview).
- Uniformity check: BOTH a local refactoring (`rename_symbol`) AND a structural refactoring (`move_class`) return `{ filesModified, diff, undoChangeId, summary }` by default; both honour `auto_apply: false` for cache-only mode.
- End-to-end smoke A (default agent loop — rename happy path): agent calls `rename_symbol(symbol="Foo", newName="Bar")` → files modified directly → `compile_workspace` returns no errors → agent reports "done". User saw one statement; no diff prompt.
- End-to-end smoke B (agent's verify-then-revert loop): agent calls `rename_symbol(...)` → files modified → `compile_workspace` returns errors → agent calls `undo_refactoring(undoChangeId)` → files restored → agent reports "tried but rolled back, diagnostic: …".
- End-to-end smoke C (opt-in preview for staged refactoring): agent calls `move_class(..., auto_apply=false)` → gets `changeId` + diff → calls `move_class(..., auto_apply=false)` again for second target → calls `apply_refactoring(changeId1)` then `apply_refactoring(changeId2)` → both commit. Used when the agent wants to stage multiple changes atomically.
- End-to-end smoke D (clone replacement via composite tool, validates the workflow the user flagged): `find_duplicate_code` → returns clone-group IDs → agent calls `replace_duplicates(cloneGroupId="abc-123")` → composite tool internally extracts canonical + replaces clones → `compile_workspace` green → done. Single tool call after detection; no per-step orchestration burden on the agent. Auto-apply throughout; any per-step failure rolls back all preceding steps via captured undo handles.
- End-to-end smoke E (replace_duplicates failure path): `replace_duplicates(...)` partway through fails on a clone replacement → composite tool rolls back the canonical extract + any successful per-clone replaces via captured `undoChangeId`s → final state is identical to pre-call state → agent reports the failure + diagnostic.

## Source planning notes

- Triggered by user observation 2026-06-04 (fork Sprint 14 Q4 review): existing refactor tools return location strings, not actions; Eclipse IDE flow is preview→simulate→agree→apply.
- Reframed 2026-06-04 (follow-up): the "preview→agree" gate is for humans; the agent's equivalent is "refactor → verify → undo if broken". Auto-apply by default; undo handle as safety net; opt-in preview only when the agent's own workflow needs it.
- Promoted to Sprint 14a 2026-06-04 (next user message): scope decision to immediately follow v1.8.0; durable policy attached; `replace_duplicates` composite added so the find-duplicate-code workflow closes in the same release as the apply layer.
- Forward link: [`sprint-17-multi-step-orchestration.md`](sprint-17-multi-step-orchestration.md) — the generic multi-step orchestration framework still belongs to Sprint 17 (rollback semantics, plan inspection, dry-run-all-then-apply transactions). 14a ships a specific composite (`replace_duplicates`) using ad-hoc step-by-step rollback; Sprint 17 generalises to arbitrary plans.
