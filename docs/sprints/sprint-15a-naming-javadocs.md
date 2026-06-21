# Fork Sprint 15a — Naming conventions + Javadoc knowledge tools

> **✅ SHIPPED — javalens-mcp v1.11.0.** Delivered: `analyze_javadocs(ingest|validate|generate)`
> + `analyze_naming(infer|get|suggest|check)`, both parametric, read-only, model-free; the
> shared `org.javalens.mcp.knowledge.SymbolFact` schema (reused by 15b + Sprint 21). Tool count
> 81 → 83. The tools redefined here as evidence+skeleton emitters were implemented exactly that
> way (generate returns a doclint skeleton + `prosePlaceholders`; suggest never invents a stem
> without `intent`). Next javalens content: 15b null-safety → v1.12.0.
>
> **Status: drafted 2026-06-21; committed as a core javalens sprint.** Inserted after Sprint 15 modernisation sweeps because naming conventions and Javadocs are core semantic-service capabilities, not late-stage experience-store polish — and not GOJA-branding work.
>
> **Placement:** between [`sprint-15-modernisation-sweeps.md`](sprint-15-modernisation-sweeps.md) and [`sprint-16-goja-rebrand.md`](sprint-16-goja-rebrand.md).
>
> **Version note (decision 2026-06-21):** this ships as a **javalens-mcp release** (the next minor after v1.10.0), **before** the GOJA rebrand — Harald's call: naming + Javadoc knowledge are core semantic-service capability that should land in javalens, not wait for the brand cutover. **This supersedes the earlier "Sprint 15 is the final javalens-branded release" note** — 15a is now the final javalens *content* sprint before the GOJA cutover. The `sprint-15-modernisation-sweeps.md` and `sprint-16-goja-rebrand.md` "final javalens release" references should be reconciled to match.

**Scaffold-level scope below; promote to actionable plan before implementation.**

## Theme

Autonomous refactoring agents need two kinds of codebase knowledge before they safely create or reshape Java code:

1. **Naming conventions** — how this codebase names services, adapters, DTOs, tests, fixtures, extension points, factories, managers, commands, and domain concepts.
2. **Javadocs** — existing API contracts, lifecycle rules, invariants, extension-point expectations, deprecations, and domain explanations anchored to Java symbols.

These are not optional documentation features. They are part of the semantic service. Naming tells the agent how new code should fit. Javadocs tell the agent what behavior and contracts it must not break.

## Product fit

This sprint sits before the local experience store because it produces high-signal knowledge that later experience-memory work can consume:

```
JDT symbol model
  + naming convention inference
  + Javadoc contract extraction
  = better refactoring plans, generated names, and API-safe edits
```

The later experience store ([`sprint-21-local-experience-store.md`](sprint-21-local-experience-store.md)) can persist accepted conventions and Javadoc-derived facts, but GOJA should be able to infer, generate, validate, and ingest them as core tools first.

## Candidate tools

Keep the tool surface parametric and compact.

> **Design invariant: this is a model-free, deterministic JDT service — it has no LLM.**
> Every tool here emits only what JDT can derive: structural facts, extracted evidence,
> and doclint-correct skeletons. **Prose and semantic naming are the agent's job** (the
> agent *is* the model). Tools that would otherwise "write good docs" or "invent a good
> name" are therefore redefined below as **evidence + skeleton emitters**: javalens supplies
> what only the compiler knows; the calling agent supplies what only a model can phrase.

### Naming conventions

- **`infer_naming_conventions(projectKey?, scope?)`** — scan packages/types/methods/fields/tests to infer naming patterns. Returns conventions with examples, confidence, scope, and exceptions.
- **`get_naming_conventions(target)`** — return conventions applicable to a package, type, member, or generated-code target.
- **`suggest_name(kind, intent, context)`** — **evidence emitter, not a name inventor.** The caller supplies the semantic `intent` (the role/purpose of the symbol — e.g. "client for the billing HTTP API"); the tool applies local conventions (casing, prefix/suffix vocabulary, package-local exemplars) to render convention-consistent **candidate** names plus the conventions it applied and the exemplars it matched. It does **not** synthesise meaning from nothing — with no `intent` it can only return the applicable convention shape (e.g. "outbound HTTP → `*Client`"), leaving the agent to fill the semantic stem. `kind` covers class, interface, enum, record, method, field, constant, test, fixture, package, extension point.
- **`check_name_against_conventions(name, context)`** — validate a proposed name and return violations, confidence, examples, and suggested alternatives.

### Javadocs

- **`ingest_javadocs(projectKey?, scope?)`** — parse existing Javadocs and return symbol-anchored knowledge entries: API contracts, lifecycle rules, invariants, deprecations, thread-safety notes, extension-point expectations, domain terms.
- **`generate_javadocs(target, style?)`** — **skeleton + evidence emitter, not a prose writer.** Emits a **doclint-correct Javadoc skeleton** for the target plus the structural evidence JDT can extract, so the agent writes the prose into a correct, complete frame:
  - one `@param` per declared parameter (in order), one `@return` for non-void (flagged nullable when a nullness annotation is present), one `@throws` per declared/propagated exception;
  - surfaced facts: visibility, `@Override`/super-method link, `@Deprecated` + any `@deprecated` tag, detected thread-safety/concurrency annotations, `@FunctionalInterface`, serialization markers;
  - `prosePlaceholders` marking exactly where human/agent prose is required (summary, `@param` descriptions, invariants), and a `skip: true` signal for trivial targets (see "Bad … targets" below) so the agent doesn't doc noise.

  The tool never fabricates behavioural prose — that would require a model it doesn't have. It guarantees the *frame* is correct and the *evidence* is complete; the agent supplies meaning.
- **`validate_javadocs(projectKey?, scope?)`** — run Javadoc/doclint-style validation and report missing, broken, stale, or low-value Javadocs. (Pure JDT/doclint — fully deterministic.)

## Agent handoff / generation boundary

GOJA is model-free (see the design invariant in *Candidate tools*): these tools emit facts, evidence, and doclint-correct skeletons — never model-written prose or an invented semantic name. The last mile (Javadoc descriptions, the semantic name stem) is supplied by whoever holds a model: **the calling MCP client today** (it receives the skeleton + `prosePlaceholders` + evidence and writes the prose), or the future GOJA/manager **agent runner**. This is **not** [Sprint 18 orchestration](sprint-18-multi-step-orchestration.md) — that layer is itself deterministic. The boundary is specified once in [`sprint-future-agent-runner.md`](sprint-future-agent-runner.md); this sprint depends on that handoff but introduces no model of its own.

## Naming convention model

Naming conventions should not be hard-coded as universal style rules. They are project-local observations with scope and confidence:

```json
{
  "type": "naming_convention",
  "scope": {
    "packages": ["com.example.billing"],
    "symbols": []
  },
  "kind": "external_integration_class",
  "summary": "Outbound integration adapters use the suffix Client, not Service.",
  "details": "Classes calling external HTTP APIs are named *Client. *Service is reserved for business services.",
  "confidence": "high",
  "examples": ["InvoiceClient", "PaymentClient"],
  "exceptions": ["LegacyPaymentService"]
}
```

The tool should distinguish:

- **Observed convention** — "this project appears to do X."
- **Strong convention** — "most matching code in this scope does X."
- **Exception** — "this symbol violates the convention, but appears intentionally legacy or framework-driven."
- **Unclear** — "not enough evidence; do not force a rename."

## Javadoc knowledge model

Existing Javadocs are source-owned. GOJA should not move them into a separate knowledge base. It should parse and surface them as symbol-anchored facts:

```json
{
  "type": "api_contract",
  "symbol": "com.example.billing.InvoiceService.calculateTotals",
  "summary": "Totals must preserve line-item rounding before invoice-level rounding.",
  "source": {
    "kind": "javadoc",
    "file": "src/main/java/com/example/billing/InvoiceService.java"
  },
  "confidence": "high"
}
```

Where `generate_javadocs` should emit a full skeleton + evidence (and the agent should then write prose):

- public/protected APIs
- extension points
- framework lifecycle methods
- non-obvious domain behavior
- invariants and pre/postconditions
- concurrency and thread-safety rules
- serialization/API compatibility rules
- deprecated behavior that must remain supported

Where it should emit `skip: true` (don't frame docs the agent/human won't maintain):

- trivial getters and setters
- comments that would restate the method name
- implementation narration
- generated noise that humans will not maintain

## Interaction with refactoring tools

The naming and Javadoc tools should feed existing and planned refactoring flows:

- `rename_symbol` should be able to call naming checks before proposing or applying a rename.
- code generation tools should call `suggest_name` before creating classes, fields, methods, tests, or fixtures.
- `plan_refactoring` should include Javadoc-derived API contracts in the plan evidence when a public/protected symbol is affected.
- modernization tools should avoid changes that contradict contract Javadocs unless the agent explicitly explains why the contract remains preserved.

## Tool-count strategy

This sprint should avoid a wide tool explosion. If Sprint 16 service consolidation is already underway, these tools can be grouped under parametric front doors:

```
conventions(kind, ...)
  kind = infer | get | suggest_name | check_name

javadocs(kind, ...)
  kind = ingest | generate | validate
```

If shipped before consolidation, the narrow tools above are acceptable as temporary names, with Sprint 16 folding them into parametric form during the GOJA cutover.

## Dependencies

- Requires JDT symbol resolution and source range mapping already present in the fork.
- Benefits from Sprint 15 modernisation sweeps because modernization suggestions need convention-aware naming and API-contract awareness.
- Feeds Sprint 18 multi-step orchestration and Sprint 21 local experience store.
- Does not require the H2 experience store. The tools can return structured results directly before persistence exists.

## Acceptance signal

- Naming inference works on focused fixtures and at least one real module.
- `suggest_name` proposes names consistent with package-local examples.
- `check_name_against_conventions` distinguishes violations from known exceptions.
- Javadocs are parsed and anchored to types, methods, and fields.
- `validate_javadocs` reports missing/broken/stale public API docs without spamming trivial getters/setters.
- `generate_javadocs` emits a doclint-correct skeleton with complete extracted evidence (`@param` per parameter, `@return` with nullness, `@throws` per declared exception) and prose placeholders for a non-trivial public method fixture — and emits `skip: true` for a trivial getter (no model-written prose anywhere in the tool output).
- `suggest_name` returns convention-consistent candidates for a supplied `intent`, and returns only the applicable convention shape (no fabricated stem) when `intent` is absent.
- Refactoring smoke: a rename/generation flow can request naming guidance before proposing a new symbol name.
- Full reactor `mvn clean verify` green when implemented.

## Source planning notes

- Architecture discussion, 2026-06-21: naming conventions and Javadocs should be core semantic service capabilities, earlier than the local experience-store sprint.
- Forward link: [`sprint-21-local-experience-store.md`](sprint-21-local-experience-store.md), which can persist accepted conventions and Javadoc-derived facts later.
- Forward link: [`sprint-18-multi-step-orchestration.md`](sprint-18-multi-step-orchestration.md), which can use Javadoc-derived contracts as plan evidence.
