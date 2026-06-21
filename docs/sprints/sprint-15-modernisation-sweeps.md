# Fork Sprint 15 — Modernisation sweeps

> **✅ SHIPPED 2026-06-21 — javalens-mcp v1.10.0 (modernisation sweeps).** (Note: this is no longer "the final javalens release" — per the 2026-06-21 decision, Sprints 15a (naming/Javadoc) and 15b (null-safety) now also land in the javalens line before the GOJA rebrand.)
> Delivered, as committed/verified:
> - **B0** bugs #14 (`health_check` real version) + #15 (`change_method_signature`
>   constructor handling); fork issues #1/#3 close at this release.
> - **B1/B2** Cursor-DX block: `FqnResolver` dot-form member fallback (fixes all 4
>   `find_*`); `analyze_method` resolved-symbol echo + nearby-candidate graceful errors +
>   example arg shapes. (DX#1/#2 were largely already shipped in v1.8.0.)
> - **B3/B4** modernisation packaged as **ONE parametric `find_modernization(kind)`** —
>   *not* 6 separate tools (Harald's call: keeps the count low under the Antigravity cap;
>   matches `find_quality_issue`). 8 kinds: anon_to_lambda, switch_to_pattern,
>   loop_to_stream, optional, class_to_record, sealed, + (B5b) lombok_to_record, delombok.
> - **B5a** `apply_cleanup` parametric catalog (add_final, redundant_modifiers) — apply/undo,
>   non-overlapping with existing tools.
> - **B5b** Lombok **removal**: the two `find_modernization` Lombok kinds + a source-only
>   `LombokDetector` (no agent).
> - **B5c** Lombok **comprehension** agent (Option B): the product bundles a pinned
>   `lombok.jar` (1.18.36, version-locked to JDT 3.39); the **manager** detects Lombok in a
>   workspace and conditionally prepends `-javaagent:lombok.jar` to the resident JVM.
>   **E2E-PROVEN**: with the agent, `get_type_members` on a `@Data` class surfaces the
>   synthesized getters; without it (control), they're absent.
> - Tool count **79 → 81**. Full reactor green; `lombok.jar` bundled in all 5 platform products.
>
> The original "+6 separate tools" scope below is superseded by the parametric design.
>
> **Status: re-sequenced 2026-06-07.** Originally drafted as Sprint 16 (SCAFFOLD). Pulled forward to **Sprint 15** when the fork roadmap reordered to ship Modernisation (Java language catch-up: lambdas, records, sealed, pattern-matching) before Fowler smell detection — modernisation tools are smaller scope (~6 tools) and ship as a tighter release.
>
> **Target version: javalens-mcp v1.10.0** (minor bump for the 6 new tools).
>
> **Predecessor:** [`sprint-14b-refactoring-full-apply.md`](sprint-14b-refactoring-full-apply.md) → v1.9.0 (refactor-apply policy + upstream parity sync).
>
> **Successor:** [`sprint-15a-naming-javadocs.md`](sprint-15a-naming-javadocs.md) + [`sprint-15b-null-safety.md`](sprint-15b-null-safety.md) (further javalens content sprints), then [`sprint-16-goja-rebrand.md`](sprint-16-goja-rebrand.md) → **goja-mcp v1.0** (the brand cutover + service consolidation for the Antigravity tool cap). The **final javalens-mcp content** lands in 15a/15b; goja-mcp v1.0 then launches the new product identity under a fresh v1.x line.
>
> **Fowler smell detection** (originally Sprint 15, the largest content sprint) moves to [`sprint-17-fowler-smell-detection.md`](sprint-17-fowler-smell-detection.md) → goja-mcp v1.1 (first content release under the GOJA brand).

**Scaffold-level scope below; promote to actionable plan when Sprint 14b enters its cutover window.**

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

## Cursor-feedback DX block (added 2026-06-11)

Source: real-agent field feedback from a Cursor session driving the latency investigation through `jl-strategies-orb` — [`../javalens_feeback_from_cursor.md`](../javalens_feeback_from_cursor.md). Five symbol-resolution / error-DX items ship in this sprint alongside the modernisation tools (the sixth feedback item, `readOnlyHint` for Ask mode, was re-scoped to the 14a hotfix line → v1.8.7):

1. **FQN method lookup.** `find_references` (and siblings) with `package.Type.method` — e.g. `com.jats2.model.portfolio.model.PortfolioManager.registerContract` — returns `SYMBOL_NOT_FOUND` today. Support `Type.method` and fully-qualified `package.Type.method` forms, including overload disambiguation via the existing FQN-overload extension from v1.8.0.
2. **`search_symbols` member coverage.** Private/local methods (`registerContract`) and fields (`ptReceiver`) are invisible to `search_symbols` even though `analyze_type` lists them. Add opt-in flags (or default inclusion) for methods, fields, private members, anonymous/nested-class members.
3. **Resolved-symbol echo.** Location-based calls silently resolve whatever token the column lands on (observed: asking near `PortfolioManager.registerContract(...)` resolved type `MarketState`). Every location-based response leads with the `symbol` it actually resolved, so agents can detect mis-resolution before trusting reference lists.
4. **Nearby candidates on position errors.** `analyze_method` → `Position is not on a method` returns the nearby candidate methods + their declaration ranges instead of a bare error.
5. **Example argument shapes in errors.** Each error response includes an example of the accepted argument shape for that tool. (The existing `hint` field already proved its worth live — an Antigravity agent self-corrected a wrong-package FQN via the `Use search_symbols` hint, 2026-06-10. This item extends that pattern.)

Acceptance: re-run the documented friction cases from the feedback doc verbatim — each one must succeed or return an actionable error with candidates/examples.

## Dependencies

- **Standalone** — no prerequisite from Sprint 14, 15, or 17.
- Java-language-version sensitive: each tool reads the project's `release` target and only suggests applicable modernisations.
- Composable with Sprint 18 multi-step orchestration if/when the framework lands — batch-apply becomes an orchestrated multi-step refactoring.

## Acceptance signal

- `health_check` +6 tools.
- Each tool has a focused test fixture: a deliberately old-style snippet + assertion that the tool flags it; a modernised snippet + assertion that the tool leaves it alone.
- Sprint exit smoke against the fork itself: `find_anonymous_for_lambda` returns at least 1 plausible candidate from real fork code.

## Source planning notes

- Manager [`docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) — "Modernisation idioms" + "Modernise to Java N" sweeps sections.
- Existing single-site tool: `convert_anonymous_to_lambda` (Sprint 11).
