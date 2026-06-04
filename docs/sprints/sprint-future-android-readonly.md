# Fork Sprint (SCAFFOLD) — Android read-only support

**Status:** scaffold only. Theme + summary + candidate items. Not an actionable plan yet.

**Sprint number TBD** — sequencing depends on whether Android-heavy users surface in our user base. Manager's [`future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) notes "Android-specific tooling is high-leverage *for Android-heavy users only*. If our user base is predominantly server-side Java, Android tooling is medium priority — Sprint 16 or later."

## Theme

Android is its own world: Gradle + Android-specific resources + lifecycle constraints. JavaLens currently sees Android projects as "a Gradle project that happens to import `android.*` packages". This sprint ships the **read-only** layer: manifest analysis, resource resolution, layout XML symbol surface, lifecycle correctness. Migration helpers (AndroidX, Compose) are bigger items deferred to a later sprint each.

Gradle dep tools shipped in Sprint 14 (fork v1.8.0 Phase C) — the build-system foundation Android needs is already there.

## Candidate items (read-only layer)

- **`analyze_manifest(filePath)`** — return declared activities/services/receivers, permissions, intent filters, exported flags. Detect `exported=true` without permission as security signal.
- **`find_resource_usages(resourceId)`** — cross-references resource IDs across Java/Kotlin and XML layouts. Big win for refactoring (rename a string resource → updates every usage).
- **`get_layout_symbols(file.xml)`** — return IDs and types declared in a layout. Enables `findViewById` correctness checks.
- **`check_lifecycle_overrides(activityClass)`** — Activity/Fragment/Service lifecycle methods: detect override correctness, missing `super.onX()` calls.
- **`find_binding_for(layout.xml)`** — find the ViewBinding/DataBinding generated source so the agent can reason about it.

## Deferred to later sprints (bigger items)

- AndroidX migration helper — `migrate_to_androidx(packagePath)`. Textual + ASTRewrite refactoring on top of pattern recognition.
- Compose-specific tooling — `@Composable` detection, recomposition impact analysis, state-hoisting suggestions. Structurally different from XML layouts; own tool family.
- Android Lint integration — `run_android_lint(projectKey)` like `compile_workspace` but for Android-specific patterns.

## Dependencies

- **Requires Gradle dep tools** (shipped Sprint 14 fork v1.8.0 Phase C).
- Independent of Sprints 15-19 (smells/Kerievsky/SOLID work at language level, orthogonal to Android).
- Detection needs Android SDK on the classpath of the loaded project (Android plugin should populate it via Buildship).

## Acceptance signal

- 5 new read-only tools shipped. `health_check` +5.
- Each tool has a focused test fixture: a minimal Android project skeleton with one activity + one layout XML; assertions match the expected manifest + resource + lifecycle outputs.
- Sprint exit smoke: load a real (small) Android project → all 5 tools return non-empty plausible results.

## Source planning notes

- Manager [`docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) — "Android territory" section.
