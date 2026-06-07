# Fork Sprint 19 — Kerievsky "Refactoring to Patterns"

> **Status: re-sequenced 2026-06-07.** Originally drafted as Sprint 18 (SCAFFOLD). Renumbered to **Sprint 19** when the GOJA rebrand claimed Sprint 16 and the post-rebrand content sprints shifted by one.
>
> **Target version: goja-mcp v1.3** (pattern-targeted refactorings).
>
> **Predecessor:** [`sprint-18-multi-step-orchestration.md`](sprint-18-multi-step-orchestration.md) → goja-mcp v1.2 (the orchestration framework this sprint REQUIRES).
>
> **Successor:** [`sprint-20-solid-detection.md`](sprint-20-solid-detection.md) → goja-mcp v1.4.

**Scaffold-level scope below; promote to actionable plan when Sprint 18 enters its cutover window.**

## Theme

Joshua Kerievsky's *Refactoring to Patterns* (2004) is the bridge between Fowler's small-step refactorings and GoF's design patterns. Each transformation is a sequence of Fowler primitives whose target is a named pattern instantiation. The catalogue runs **both directions** — *toward* patterns when complexity warrants, *away from* patterns when they've outlived their usefulness (Inline Singleton, Replace Pattern with Idiom).

This sprint ships pattern-targeted refactorings as compositions of existing primitives. **Requires Sprint 18's orchestration framework** to land first.

## Candidate items (representative slice — ~8 transformations)

Toward patterns:
- `refactor_to_command_dispatcher(target, kind)` — Replace Conditional Dispatcher with Command. Detection: `switch` / if-else chain over type-coded action with N+ non-trivial-body cases.
- `refactor_to_visitor(target)` — Move Accumulation to Visitor. Detection: method accumulates info across tree via `instanceof`.
- `refactor_to_state(target)` — Replace State-Altering Conditionals with State. Detection: N+ if-else branches on same internal field.
- `compose_method(target)` — Compose Method. Detection: long method with disjoint sections.
- `replace_type_code_with_class(target)` — Replace Type Code with Class. Detection: `int` / `String` passed around as domain code.
- `form_template_method(parent, children)` — Form Template Method. Detection: two near-identical methods differing in a few steps.

Away from patterns (the distinguishing Kerievsky insight):
- `inline_singleton(target)` — Singleton class whose lifecycle/uniqueness no longer matters (e.g. now DI-managed).
- `replace_pattern_with_idiom(target, pattern)` — Pattern wrapped around a language feature that has since landed (Iterator-as-class when streams exist; Visitor when pattern-matching switch exists).

## Bidirectional detection

The "away" direction is what distinguishes Kerievsky from GoF. Detection rules for "patterns hurting":
- Single-implementation interfaces (Lazy Class / Speculative Generality).
- Abstract methods overridden in only one place.
- Patterns wrapped around language features that have since landed (per the "away" list above).

## Dependencies

- **Requires Sprint 17** (orchestration framework). Each Kerievsky transformation is a multi-step plan; the orchestrator executes them.
- Builds on Sprint 15's smell detection (smell at a location → applicable Kerievsky pattern).
- Builds on existing Sprint 11 LTK refactorings (move, extract, encapsulate) — these are the primitives the recipes call.

## Acceptance signal

- 8 new transformation tools shipped. `health_check` +8.
- Each transformation has a focused test fixture + assertion that pre-fixture's smell maps to the right transformation; post-application code compiles + tests stay green.
- Sprint exit: at least one "toward" + one "away" transformation runs end-to-end against a real fork-codebase target.

## Source planning notes

- Manager [`docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) — "Worked example: Kerievsky's pattern catalog" section.
- Forward link: [`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md) for the eventual unified `find_target_candidates(catalog="kerievsky", kind)` framing.
