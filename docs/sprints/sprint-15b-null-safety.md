# Fork Sprint 15b — Java null-safety tools

> **Status: drafted 2026-06-21; committed as a core javalens sprint.** This is the *deepest* item in the modernization program (the Sprint 15 theme): **null-safety is the single biggest reason teams leave Java for Kotlin**, so closing that gap is modernization, not a side "safety" feature. It stays entirely inside the JDT/JLS toolchain.
>
> **Placement:** after [`sprint-15a-naming-javadocs.md`](sprint-15a-naming-javadocs.md) and before [`sprint-16-goja-rebrand.md`](sprint-16-goja-rebrand.md).
>
> **Version note (decision 2026-06-21):** ships as a **javalens-mcp release**, **before** the GOJA rebrand — same call as 15a: this is core semantic-service / modernization capability that belongs in javalens. **This supersedes the earlier "Sprint 15 is the final javalens-branded release" note.** Reconcile the stale "final javalens release" references in `sprint-15-modernisation-sweeps.md` + `sprint-16-goja-rebrand.md`.
>
> **Relationship to `find_modernization`:** null-safety is modernization, but it carries multi-family annotation detection, flow analysis, source-mutating apply, and family migration — too rich for the find-only `find_modernization` surface. It ships as its own parametric `null_safety(kind)` tool (below). **Deconflict:** `find_modernization`'s `optional` kind (nullable-return → `Optional`) overlaps this sprint's nullable-return detection — resolve it the way `apply_cleanup` was split from `find_modernization`: `find_modernization(optional)` owns the *Optional refactor suggestion*; `null_safety` owns *nullness contracts + annotations* and does **not** re-suggest the Optional rewrite.

**Scaffold-level scope below; promote to actionable plan before implementation.**

## Theme

Kotlin's strongest ergonomic safety advantage over Java is built-in nullability — and it's the advantage most often cited when teams leave Java. Java can recover much of that benefit through explicit null contracts, compiler/static-analysis checks, and local modernization tools. This sprint is therefore the **deepest slice of the modernization program**: it adds tools that infer, validate, and apply Java null-safety annotations without leaving the Java/JDT ecosystem.

> **Build on the compiler, don't rebuild it.** ECJ already performs annotation-based and flow null analysis. The detection tools below should *enable JDT's compiler null options* (`org.eclipse.jdt.core.compiler.problem.nullReference`, `…nullSpecViolation`, `…nonnullParameterAnnotationDropped`, `@NonNullByDefault` handling) and read the resulting `IProblem` markers — not hand-roll dataflow. Inference layers the project-specific signals (below) on top of that compiler baseline.

The goal is not to make Java identical to Kotlin. The goal is to make enterprise Java codebases safer and more explicit:

```
JDT bindings
  + flow-sensitive null analysis
  + project annotation style detection
  = explicit null contracts agents can preserve during refactoring
```

## Annotation ecosystem

GOJA should understand multiple nullness annotation styles because enterprise Java codebases vary:

- **JSpecify** — future-facing standardization target. Prefer for new projects when no project-local standard exists.
- **Eclipse JDT annotations** — natural fit for GOJA because JDT already understands them (`@NonNull`, `@Nullable`, `@NonNullByDefault`).
- **JetBrains annotations** — common in IntelliJ-heavy codebases.
- **javax / FindBugs / SpotBugs annotations** — common legacy forms.
- **Checker Framework annotations** — strongest formal model, but higher adoption cost.
- **NullAway / Error Prone configuration** — practical large-codebase enforcement path.

The first implementation should detect the existing project style and avoid mixing annotation families without an explicit migration request.

## Candidate tools

Keep the tool surface compact and parametric where possible. Ship the parametric front door from day one (don't ship 6 narrow tools then consolidate in Sprint 16 — same call as `find_modernization`):

```
null_safety(kind, ...)
  read-only:  detect_style | infer_contracts | find_violations | check
  applying:   add_annotations | migrate_annotations
```

> **Two contracts, by kind.**
> - **Read-only kinds** (`detect_style`, `infer_contracts`, `find_violations`, `check`) are deterministic JDT analysis — no LLM. Any "explain why the contract changed" narration is the *agent's* job; these tools emit structured findings + evidence + confidence only.
> - **Applying kinds** (`add_annotations`, `migrate_annotations`) mutate source, so per the v1.9.0 durable refactoring policy (PR-review gate) they MUST go through `AbstractApplyingRefactoringTool`: build a JDT `Change`, apply by default, cache the undo, return `{ filesModified, diff, undoChangeId, summary }`; accept `auto_apply:false` to stage.

- **`detect_nullness_style(projectKey?, scope?)`** — identify the annotation family already used in the project, package defaults, build plugins, and enforcement tools. *(read-only; trivial — ship first.)*
- **`find_nullness_violations(projectKey?, scope?)`** — enable JDT's compiler null options and report the resulting probable null dereferences, nullable-to-non-null flows, missing checks, and override contract mismatches (compiler markers + override resolution; no hand-rolled dataflow). *(read-only.)*
- **`infer_null_contracts(projectKey?, scope?)`** — infer likely nullable/non-null parameters, returns, fields, and local contracts by layering the project signals (assignments, dereferences, guards, `Optional` usage, annotations, overrides, call sites, Javadoc evidence) on top of the compiler baseline. *(read-only.)*
- **`add_null_annotations(target, style?, mode?)`** — add or update nullness annotations for a type, package, method, or field, via the apply/undo contract. `style` defaults to detected project style; `mode` controls conservative (default) vs aggressive inference. **Public-API guard:** defaults to non-public scope; annotating an exported/public API (a breaking downstream contract change) requires explicit opt-in.
- **`migrate_null_annotations(from, to, scope?)`** — convert annotation families, via the apply/undo contract. **Highest-risk, ship last:** family semantics are *not* 1:1 — e.g. JSpecify's `@NullMarked` default-scoping ≠ Eclipse `@NonNullByDefault`, and JSR-305/JetBrains differ on field/array/type-use targeting — so migration must preserve *meaning*, not just swap names. Start with one or two well-understood pairs; refuse ambiguous conversions rather than guess.
- **`check_null_safety(target)`** — focused validation for a symbol or file before/after refactoring. *(read-only.)*

## Agent handoff / generation boundary

GOJA is model-free (see *Candidate tools* — read-only kinds analyse, applying kinds mutate via the apply/undo contract; none write prose). The structural change is deterministic; the *rationale narrative* ("why this contract changed and why callers are unaffected") is supplied by whoever holds a model: **the calling MCP client today**, or the future GOJA/manager **agent runner** — **not** [Sprint 18 orchestration](sprint-18-multi-step-orchestration.md), which is itself deterministic. The boundary is specified once in [`sprint-future-agent-runner.md`](sprint-future-agent-runner.md); this sprint depends on that handoff but introduces no model of its own.

## Inference rules

Start conservative. Nullness annotations become API contracts, so false confidence is worse than missing an annotation.

High-confidence non-null signals:

- immediate dereference without guard after construction or required injection
- constructor-initialized final field
- parameter validated by `Objects.requireNonNull`
- method documented or annotated as non-null in an overridden/implemented contract
- `Optional<T>` return used instead of nullable return

High-confidence nullable signals:

- explicit `return null`
- branch assigns `null`
- call sites check result for null
- existing `@Nullable` in overridden/implemented contract
- Javadoc says "may return null" or equivalent

Risky / low-confidence signals:

- framework injection fields
- serialization/deserialization DTOs
- reflection-populated objects
- generated code
- public APIs with unknown external callers
- method overrides where superclass/interface annotation is absent

## Interaction with Javadocs

Sprint 15a Javadoc tooling and Sprint 15b null-safety tooling should reinforce each other:

- Javadocs that say "never null", "may be null", "returns null if..." become evidence for `infer_null_contracts`.
- `add_null_annotations` should update Javadocs only when the contract is clear and the doc currently contradicts the annotation.
- `validate_javadocs` should flag mismatch between nullness annotations and Javadoc text.
- Sprint 21 can persist accepted nullness conventions and null-contract lessons as experience entries.

## Interaction with refactoring tools

Null-safety contracts should become part of refactoring evidence:

- `change_method_signature` must preserve or explicitly explain null-contract changes.
- `extract_method` should infer nullness for extracted method parameters/return values from the original flow.
- `move_method`, `pull_up`, and `push_down` must respect override contracts.
- `generate` tools should emit nullness annotations consistent with project style.
- modernization tools should avoid replacing null-return APIs with `Optional` unless API compatibility and call-site migration are in scope.

## Dependencies

- Requires JDT bindings, AST traversal, override resolution, the apply/undo `Change` infrastructure (`AbstractApplyingRefactoringTool`), and JDT's compiler null-analysis options.
- Benefits from Sprint 15a Javadocs because Javadocs provide null-contract evidence.
- **Shares the `symbol_fact` evidence schema with 15a** (`{ type, symbol, summary, source, confidence }`): a null contract is `type:"null_contract"` with `nullness` + evidence list. Define the schema once (15a) and reuse it here, so 18 (plan evidence) and 21 (experience store) consume one shape.
- Feeds Sprint 18 multi-step orchestration and Sprint 21 local experience store.
- Does not require H2 persistence; tools can return structured findings before accepted contracts are stored.

## Acceptance signal

- `detect_nullness_style` identifies Eclipse/JDT, JSpecify, JetBrains, and legacy annotation fixtures.
- `find_nullness_violations` is driven by JDT compiler null markers (not hand-rolled flow) and detects focused fixture cases: obvious dereference, nullable return unchecked, override mismatch.
- `infer_null_contracts` returns conservative parameter/return/field contracts with evidence and confidence — and returns *no* contract (not a guessed one) on the risky-signal fixtures (framework injection, DTO, generated code).
- `add_null_annotations` annotates a focused fixture using the detected style, preserves formatting/imports, **applies via the undo contract (round-trip restores byte-for-byte)**, and **refuses a public-API target without the explicit opt-in**.
- `migrate_null_annotations` converts one well-understood family pair on a small fixture via the undo contract, and **refuses an ambiguous conversion** rather than guessing.
- Javadoc mismatch fixture: nullable annotation + "never null" doc is reported.
- Refactoring smoke: extracted method receives a conservative nullness contract based on source flow.
- Full reactor `mvn clean verify` green when implemented.

## Source planning notes

- Architecture discussion, 2026-06-21: Java can recover a meaningful part of Kotlin's null-safety benefit through explicit annotations and static analysis while preserving the Java/JDT autonomous-refactoring thesis.
- Related: [`sprint-15a-naming-javadocs.md`](sprint-15a-naming-javadocs.md), especially Javadoc contract extraction.
- Forward link: [`sprint-21-local-experience-store.md`](sprint-21-local-experience-store.md), which can persist accepted nullness conventions and null-contract lessons later.
