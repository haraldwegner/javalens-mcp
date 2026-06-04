# Fork Sprint 16 (SCAFFOLD) — Modernisation sweeps

**Status:** scaffold only. Theme + summary + candidate items. Not an actionable plan yet.

## Theme

Find-and-batch-apply for Java language evolution. JDT already knows what `var` / records / sealed types / pattern-matching switch look like — the agent just needs tooling to spot every place applying them shrinks code without losing clarity. Each modernisation is a *find* + *batch-apply* pair, so the tool budget is small (~6 tools) while the leverage is broad.

## Candidate items

- `find_anonymous_for_lambda(projectKey?)` — every anonymous class that can become a lambda. We have `convert_anonymous_to_lambda` per-site since Sprint 11; this is the codebase-wide find + batch apply.
- `find_switch_to_pattern(projectKey?)` — every `switch` statement that could become a Java 21 pattern-matching switch expression.
- `find_loop_to_stream(projectKey?)` — every `for` / enhanced-for loop matching the well-known Collector patterns (map, filter, reduce, toList).
- `find_optional_introduction(projectKey?)` — every nullable return chain that could be `Optional` + `.map().orElse()`.
- `find_class_to_record(projectKey?)` — every plain data class (fields + ctor + getters + equals/hashCode/toString, no other behaviour) that fits Java 16 records.
- `find_sealed_candidates(projectKey?)` — every abstract base + closed-set subclass hierarchy that fits Java 17 sealed types.

Each find tool returns ranked candidates with location + before/after sketch. Each pairs with a per-site or batch apply (some already exist; some new).

## Dependencies

- **Standalone** — no prerequisite from Sprint 14, 15, or 17.
- Java-language-version sensitive: each tool reads the project's `release` target and only suggests applicable modernisations.
- Composable with Sprint 17 multi-step orchestration if/when the framework lands — batch-apply becomes an orchestrated multi-step refactoring.

## Acceptance signal

- `health_check` +6 tools.
- Each tool has a focused test fixture: a deliberately old-style snippet + assertion that the tool flags it; a modernised snippet + assertion that the tool leaves it alone.
- Sprint exit smoke against the fork itself: `find_anonymous_for_lambda` returns at least 1 plausible candidate from real fork code.

## Source planning notes

- Manager [`docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) — "Modernisation idioms" + "Modernise to Java N" sweeps sections.
- Existing single-site tool: `convert_anonymous_to_lambda` (Sprint 11).
