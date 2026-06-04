# Fork Sprint 15 (SCAFFOLD) — Fowler smell detection

**Status:** scaffold only. Theme + summary + candidate items. Not an actionable plan yet.

## Theme

**The biggest single gap in our tool surface vs "best Java dev".** Fowler's small-step refactorings (extract method, rename, etc.) tell the agent *how* to refactor. Fowler's smell catalog tells it *where* and *why*. We ship the first today; we ship none of the second. Result: the agent can refactor anything you point at, but cannot find what to refactor.

This sprint closes that gap with ~18 detection tools, each a small AST-walk + heuristic. After this sprint the agent's loop shifts from "user told me what to fix" → "I scanned the codebase and propose this prioritised list of fixes".

## Sprint shape (Phase 0 + Phase A)

Decided 2026-06-04 to bundle the **upstream-parity audit** (see [`sprint-future-upstream-parity-audit.md`](sprint-future-upstream-parity-audit.md)) as Phase 0 of this sprint:

- **Phase 0 (1-2 days)** — clone upstream `pzalutski-pixel/javalens-mcp` v1.3.5, diff against our pre-fork point, produce a porting list. If cheap cherry-picks (small fixes, schema improvements) surface, fold them into the sprint; if bigger items (Bazel detection rework, annotation-processor handling) surface, defer to Sprint 16. **No coupling between Phase 0 outcome and the rest of the sprint** — Fowler smell detection proceeds regardless.
- **Phase A (~10-14 days)** — the 18 Fowler smell-detection tools below. Each is an independent AST-walk + heuristic; ship in batches of 3-4 to keep PRs reviewable.

## Why now

- Highest single-sprint leverage gain per the manager's [`future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) sequencing recommendation.
- Each smell-detection tool is independent — ~1-2 days of JDT-AST work each — so the sprint scales linearly with how many smells we include.
- Foundation for Sprint 18 (Kerievsky patterns) — smell detection feeds the "where do I apply this pattern?" question.
- Forward-compatible with target-form catalogs (see [`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md)) — these 18 tools later become catalog-kinds under the parametric framing.

## Candidate items (rough first pass)

The 18 smells from Fowler 2nd ed. (per manager `future-sprint-enhancements.md` smell catalog):

| Smell | Detection heuristic | Pointed refactoring |
|---|---|---|
| Long Method | method LOC > N, cyclomatic > M | Extract Method |
| Large / God Class | member count > N, LOC > M, high fan-in | Extract Class / Subclass / Interface |
| Long Parameter List | `parameters().size() > 4` | Introduce Parameter Object |
| Divergent Change | needs git-history correlation | Extract Class |
| Shotgun Surgery | symbol's refs span > N classes/packages | Move Method / Inline Class |
| Feature Envy | method calls foreign-type members more than own | Move Method |
| Data Clumps | same param-name tuple recurring | Extract Class / Introduce Parameter Object |
| Primitive Obsession | long param lists of `int` / `String` | Replace Type Code with Class |
| Switch Statements | `switch` over type code | Replace Conditional with Polymorphism |
| Parallel Inheritance | structurally parallel sub-hierarchies | Move Method/Field to merge |
| Lazy Class | few members, low fan-in, no public surface | Inline Class / Collapse Hierarchy |
| Speculative Generality | abstract w/ 1 impl; unused params | Inline Class / Remove Parameter |
| Temporary Field | field used only in one method | Extract Class |
| Message Chains | `a.b().c().d()` length > N | Hide Delegate |
| Middle Man | class only delegates | Remove Middle Man |
| Inappropriate Intimacy | two classes access each others' internals | Move Method/Field |
| Refused Bequest | subclass overrides parent w/ `UnsupportedOperationException` | Replace Inheritance with Delegation |
| Duplicated Code | already covered by Sprint 14 `find_duplicate_code` (B.3) — skip here |

**Tool sketch:** `find_smell(kind, projectKey?, threshold?)` parametric — same pattern as Sprint 11 `find_pattern_usages(kind, query)` / `find_quality_issue(kind, …)`. Returns ranked candidates with location + heuristic score + pointer to applicable refactoring(s).

## Dependencies

- **Independent of Sprint 14.** No prerequisite from v1.8.0 (B.3's clone detector lives parallel to this).
- Some smells need git history (Divergent Change, Shotgun Surgery) — straightforward `git log` shell-out from JavaLens.
- Target-form catalogs work ([`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md)) can layer on top after.

## Acceptance signal

- `health_check` reports +18 tools (76 → 94 per workspace).
- Each smell has at least one focused test fixture + assertion that the heuristic flags the deliberately-smelly fixture and ignores a clean fixture.
- Sprint exit grep: `find_smell(kind="long_method")` on the fork itself → returns at least 2 plausible candidates (the fork has known long methods).

## Source planning notes

- Manager [`docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) — "Half 2 — Smell detection" section has the full heuristic table.
- Forward link: [`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md) for the eventual parametric `find_target_candidates(catalog="fowler_smell", kind)` framing.
