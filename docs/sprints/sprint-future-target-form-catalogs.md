# Fork Sprint (SCAFFOLD) — Unified target-form catalog framework

**Status:** scaffold only. Theme + summary + candidate items. Not an actionable plan yet.

**Sprint number TBD** — this is the eventual unifying layer over Sprints 15 (Fowler smells), 18 (Kerievsky patterns), 19 (SOLID), and 16 (modernisation). Likely Sprint 20 or later, once enough catalog-kinds exist to motivate the consolidation.

## Theme

After Sprints 15-19 we'll have ~30+ detection tools across multiple catalogs: 18 Fowler smells, 8 Kerievsky patterns, 5 SOLID principles, 6 modernisations. Each is a separate tool in the `tools/list` surface. This sprint **consolidates them into one parametric tool family** with a typed `(catalog, kind)` enum, following the Sprint 11 precedent (`find_pattern_usages(kind, query)` / `find_quality_issue(kind, …)`).

The consolidation is API-shape only — every existing catalog tool stays at its current implementation. The new tool delegates to the existing tool per `(catalog, kind)` pair. After a migration window the old tool surface can be deprecated, but agents discover everything through the new parametric tool's `kind` enum in `tools/list`.

## The unifying shape

```
find_target_candidates(catalog, kind, projectKey?, threshold?)
  → list of { location, applicable_target, evidence, confidence }
get_target_recipe(catalog, kind, target_location)
  → returns canonical Fowler-primitive sequence to apply, plus catalog-specified target shape
plan_refactoring(target_location, recipe_id)
  → ties to Sprint 17 multi-step orchestration framework
```

Where `catalog` is one of `{fowler_smell, kerievsky_pattern, kerievsky_anti, gof, solid, grasp, beck_simple, ddd_violation, layer_violation, modernisation, project_convention, anti_pattern}`. Each catalog's kinds are a typed enum in the schema.

## Candidate items

- **`find_target_candidates(catalog, kind, ...)`** — the parametric front door. Dispatches to per-(catalog, kind) detection logic implemented in Sprints 15-19.
- **`get_target_recipe(catalog, kind, ...)`** — returns the multi-step Fowler-primitive sequence to apply for the matched location. Bridges to Sprint 17's `plan_refactoring`.
- **Catalog registry** — extensible registry of `(catalog, kind) → detector + recipe` mappings. Adding a new kind in a future sprint is one new file + one registry entry.
- **Deprecation plan** — soft-deprecate the per-catalog tools (`find_smell`, `find_solid_violation`, etc.) in favor of the parametric front door. Both stay working through one minor-version cycle.
- **Bidirectional catalog support** — Kerievsky-style "toward pattern" + "away from pattern" cases both live under `kind`.

## Catalog conflict + arbitration

Catalogs disagree. Examples (per manager `future-sprint-enhancements.md`):
- Fowler "Long Method" vs Beck "fewest elements" — extract only if name adds intent.
- Kerievsky "Strategy" vs Speculative Generality — don't apply Strategy with one implementation.
- DDD "Bounded Context" vs Hexagonal "single dependency direction" — ACL pattern.
- Modernisation "use records" vs project-convention "use Lombok @Data" — defer to project convention.

The tool layer **supplies the inputs** to arbitration (which catalogs flag this location? what's the codebase convention here?) but does NOT make the call. The agent (Claude) arbitrates. This is the cleanest seam between tooling and model — and the strongest argument for "ship affordances, fine-tune judgment later".

## Dependencies

- **Requires** Sprints 15 (Fowler), 16 (Modernisation), 17 (Orchestration), 18 (Kerievsky), 19 (SOLID).
- This sprint **consolidates** their output; doesn't add new detectors itself.
- Future sprints adding GRASP, DDD violation detection, project-convention learning, anti-pattern detection each add new (catalog, kind) entries without touching the framework.

## Acceptance signal

- 2 new tools: `find_target_candidates`, `get_target_recipe`. `health_check` +2.
- All catalog-kinds from Sprints 15-19 discoverable through the parametric front door.
- Per-catalog tools soft-deprecated with pointers to the parametric replacement.
- End-to-end smoke: agent calls `find_target_candidates(catalog="fowler_smell", kind="long_method")` → gets candidates → `get_target_recipe(catalog="fowler_smell", kind="long_method", target_location=...)` → gets multi-step recipe → `plan_refactoring(...)` → orchestrator applies.

## Source planning notes

- Manager [`docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) — "Target-form catalogs" section has the full 11-catalog rationale.
- This is the eventual end state — Sprints 15-19 ship the detection content; this sprint ships the unifying API surface.
