# Fork Sprint (SCAFFOLD) — Android legacy Java/XML read-only adapter

**Status:** scaffold only, downgraded 2026-06-21 after roadmap discussion. Theme + summary + candidate items. Not an actionable plan yet.

**Sprint number TBD** — optional adapter only. Do not schedule on the core GOJA roadmap unless Java/XML-heavy Android users surface. Manager's [`future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) notes "Android-specific tooling is high-leverage *for Android-heavy users only*." The 2026-06-21 product direction narrows GOJA toward autonomous Java enterprise maintenance, where JDT/LTK provide the strongest refactoring substrate.

## Theme

Android is its own world: Gradle/AGP, resources, manifests, lifecycle constraints, and now a Kotlin-first application stack. GOJA's core strength is Java semantics through JDT and Java refactoring through Eclipse LTK. That strength does **not** extend to Kotlin semantics, Compose, coroutines, Flow, Kotlin compiler plugins, or Android Studio's Kotlin/AGP model.

This scaffold therefore covers only a narrow read-only adapter for **legacy or mixed Android projects with meaningful Java/XML surface area**:

- AndroidManifest.xml context
- XML resource/layout symbol extraction
- Java activity/service lifecycle checks where JDT can analyze the Java source
- generated binding source discovery when it helps Java-side reasoning

This is not full Android support.

Gradle dep tools shipped in Sprint 14 (fork v1.8.0 Phase C) — the build-system foundation Android needs is already there.

## Strategic decision

Do **not** frame GOJA as a broad JVM/Android refactoring platform. Kotlin-first Android would require a separate semantic stack: Kotlin Analysis API/K2, IntelliJ/Kotlin plugin APIs, Android Lint/AGP integration, or a Kotlin language server/indexer. That is a different product from JDT-backed autonomous Java refactoring.

GOJA may still provide read-only Android context for Java-heavy legacy projects, but Android should remain an optional adapter, not a core roadmap item.

## Candidate items (read-only layer)

- **`analyze_manifest(filePath)`** — return declared activities/services/receivers, permissions, intent filters, exported flags. Detect `exported=true` without permission as security signal.
- **`find_resource_usages(resourceId)`** — cross-references resource IDs across Java sources and XML layouts only. Kotlin usage is explicitly out of scope unless/until GOJA gains Kotlin semantic infrastructure.
- **`get_layout_symbols(file.xml)`** — return IDs and types declared in a layout. Enables `findViewById` correctness checks.
- **`check_lifecycle_overrides(activityClass)`** — Activity/Fragment/Service lifecycle methods in Java classes only: detect override correctness, missing `super.onX()` calls.
- **`find_binding_for(layout.xml)`** — find the ViewBinding/DataBinding generated source so the agent can reason about it.

## Explicitly out of scope

- Kotlin semantic analysis.
- Kotlin refactoring.
- Compose analysis.
- coroutine/Flow analysis.
- Android Studio plugin integration.
- Kotlin resource usage correctness.
- AndroidX migration.
- Android Lint integration as a GOJA-owned tool family.

## Dependencies

- **Requires Gradle dep tools** (shipped Sprint 14 fork v1.8.0 Phase C).
- Independent of Sprints 15-21. This is an optional adapter; the main autonomous-maintenance roadmap should not wait for it.
- Detection needs Android SDK on the classpath of the loaded project where Java code imports Android APIs.
- Kotlin support requires a separate future architecture decision, not a small extension of this sprint.

## Acceptance signal

- 5 new read-only tools shipped only if this adapter is scheduled. `health_check` +5.
- Each tool has a focused test fixture: a minimal Java/XML Android project skeleton with one Java activity + one layout XML; assertions match the expected manifest + resource + lifecycle outputs.
- Sprint exit smoke: load a real (small) Android project → all 5 tools return non-empty plausible results.
- Kotlin-only and Compose-only projects are documented as unsupported rather than partially analyzed.

## Source planning notes

- Manager [`docs/sprints/future-sprint-enhancements.md`](../../../javalens-manager/docs/sprints/future-sprint-enhancements.md) — "Android territory" section.
- 2026-06-21 roadmap discussion: Android is Kotlin-first; GOJA's strongest business case is autonomous Java enterprise maintenance via JDT/LTK, not broad JVM/Android support.
