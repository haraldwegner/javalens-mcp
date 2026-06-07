# Fork Sprint 20 — SOLID violation detection

> **Status: re-sequenced 2026-06-07.** Originally drafted as Sprint 19 (SCAFFOLD). Renumbered to **Sprint 20** when the GOJA rebrand claimed Sprint 16 and the post-rebrand content sprints shifted by one.
>
> **Target version: goja-mcp v1.4**.
>
> **Predecessor:** [`sprint-19-kerievsky-refactoring-to-patterns.md`](sprint-19-kerievsky-refactoring-to-patterns.md) → goja-mcp v1.3.
>
> After Sprint 20 the v1.x line is in a position to consolidate via the eventual target-form catalog framework ([`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md)) and eventually push to **goja-mcp v2.0** (which would also pick up the networked-service vision and/or RL execution work).

**Scaffold-level scope below; promote to actionable plan when Sprint 19 enters its cutover window.**

## Theme

Detection for the five SOLID principles (Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion). LSP/ISP/DIP are mechanically computable from JDT bindings; SRP and OCP need git-history correlation but the shell-out is small.

Overlaps with Sprint 17's Fowler smells — SOLID violations are a subset of Fowler smells dressed differently. Ship after Sprint 17 to ride on its smell-detection infrastructure.

## Candidate items

Parametric: **`find_solid_violation(kind, projectKey?, threshold?)`** where `kind ∈ {srp, ocp, lsp, isp, dip}`. Each kind has its own heuristic.

| Principle | Detection heuristic | Target shape |
|---|---|---|
| **S** — Single Responsibility | High class LOC + many distinct topic clusters in member-name vocabulary + high git churn correlated with multiple topic axes | Extract Class along topic boundary |
| **O** — Open/Closed | Class modified for every new feature in some feature dimension (needs git history over last N commits) | Introduce abstraction at modification axis |
| **L** — Liskov Substitution | Subclass overrides that strengthen preconditions (`throws`), weaken postconditions, partially-implement (`UnsupportedOperationException`) | Replace inheritance with composition OR split hierarchy |
| **I** — Interface Segregation | Interface with N+ methods where each implementation only calls a measurable subset | Split fat interface along call-pattern clusters |
| **D** — Dependency Inversion | Concrete-type field decls / params / returns where abstract supertype exists AND is the actual usage shape | Introduce abstraction at dependency edge |

**Tool sketch:** under the unified target-form catalogs framing (see [`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md)), this is `find_target_candidates(catalog="solid", kind="srp"|"ocp"|"lsp"|"isp"|"dip", projectKey?)` — same parametric pattern that handles Fowler smells, Kerievsky patterns, modernisation idioms.

## Dependencies

- **Requires Sprint 15** Fowler smell-detection infrastructure (heuristic-walk helpers; ranking scaffolding; threshold conventions).
- SRP/OCP shell-out to `git log` — straightforward; same path the Divergent-Change / Shotgun-Surgery smell tools would use in Sprint 15.
- LSP / ISP / DIP via JDT bindings + call-graph analysis — no external dependencies.

## Acceptance signal

- 5 new sub-kinds (or 5 new tools if not folded into parametric `find_target_candidates`). `health_check` +5 or one tool with `kind` enum of 5.
- Each principle has a focused test fixture: a deliberately-violating snippet + assertion that the heuristic flags it; a compliant snippet + assertion that the heuristic leaves it alone.
- Sprint exit: `find_solid_violation(kind="dip")` on a fork module → returns at least 1 plausible candidate (the fork has concrete-type fields where interfaces exist).

## Source planning notes

- Manager [`docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) — "Worked example: SOLID" section has the full heuristic table.
- Forward link: [`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md) for the parametric framing.
