# Fork Sprint (future) — Agent runner: the generation boundary

> **Status: scaffold, unscheduled (drafted 2026-06-21 from architecture discussion).** Captures *where model-driven content generation lives* relative to the deterministic JDT engine, so the recurring "…and the agent writes the prose" handoff in other sprints has one named home instead of being re-invented per sprint.
>
> **Target version: TBD** — likely a GOJA product slice (goja-mcp or javalens-manager side), after the core knowledge tools (15a/15b) and the orchestration framework (Sprint 18). Not on the committed javalens line.

**Scaffold-level scope; promote to actionable plan when a model-driven generation feature is actually scheduled.**

## Why this exists

GOJA is a **model-free, deterministic JDT service** by design. That invariant is load-bearing: it keeps the engine testable, reproducible, and free of API keys, token budgets, and nondeterministic output. But several planned tools produce a deliberate handoff where the *last mile* is prose or semantic judgement that only a language model can supply:

- `generate_javadocs` ([15a](sprint-15a-naming-javadocs.md)) — emits a doclint-correct **skeleton + evidence**; the *prose* is not written by the tool.
- `suggest_name` ([15a](sprint-15a-naming-javadocs.md)) — applies conventions to a caller-supplied **intent**; it does not invent the semantic stem.
- null-contract change explanations ([15b](sprint-15b-null-safety.md)) — the tool states the structural change; the *rationale narrative* is model work.
- future Fowler/Kerievsky explanations ([17](sprint-17-fowler-smell-detection.md)/[19](sprint-19-kerievsky-refactoring-to-patterns.md)) — "why this smell, why this pattern" prose.

The model that does that work has to live **somewhere outside the JDT engine**. This sprint names that place.

## The three coordination layers (only one has a model)

| Layer | Role | Has a model? |
|---|---|---|
| JDT tools (15a/15b, `find_*`, `apply_*`, codegen) | deterministic facts, evidence, doclint skeletons, mechanical source edits | **No** |
| Orchestration ([Sprint 18](sprint-18-multi-step-orchestration.md)) | sequences those primitives, validates each step, rolls back on failure | **No** |
| **Agent runner** (this doc) | pipes GOJA's structured context to an external agent CLI, captures the generated content, feeds it back through the deterministic apply/undo contract | **Yes** |

**Sprint 18 is NOT this.** Orchestration sequences deterministic primitives; it has no model and cannot write prose. Conflating the two is the trap this doc prevents.

## Two homes for "the agent part"

1. **The calling MCP client (today, zero new infra).** When Claude Code / Cursor / etc. call `generate_javadocs`, the *client* is the model: it receives the skeleton + `prosePlaceholders` + evidence and fills the prose, then calls the apply/write tool. For 15a/15b this is the default and requires nothing new — it is why the model-free split is correct rather than lossy.
2. **A GOJA/manager-driven agent runner (this sprint).** For a one-call "document this method → finished Javadoc" experience that does not depend on the client being an LLM, GOJA (or the manager) pipes structured context to an external agent CLI — Claude CLI, Cursor CLI, Gemini CLI, Grok CLI (the product shape already named in [Sprint 21 §Product roadmap fit #2](sprint-21-local-experience-store.md)). The CLI returns prose; the runner re-applies it through the deterministic apply/undo contract.

## Design questions (to resolve when scheduled)

- **Where does it run** — inside goja-mcp as an optional tool family, or in javalens-manager as the orchestrating control plane? (Leaning manager: it already owns process spawning, config, and per-workspace lifecycle.)
- **Which runners** — pluggable CLI adapters (Claude/Cursor/Gemini/Grok) behind one interface; per-workspace selection + credentials.
- **Re-entry through the apply contract** — generated prose/edits must come back as a JDT `Change` so they inherit diff + `undoChangeId` (no raw text application). The model proposes; the deterministic layer applies.
- **Determinism & caching** — model output is nondeterministic; cache by (skeleton-hash + evidence-hash) and surface it as advisory, never as a silent source-of-truth edit.
- **Boundary discipline** — the engine must never gain a hard dependency on a model being present; the agent runner is strictly additive and optional.

## Relationships

- Consumes the skeletons/evidence/`symbol_fact` output of [15a](sprint-15a-naming-javadocs.md) and [15b](sprint-15b-null-safety.md).
- Distinct from (but composes with) [Sprint 18 orchestration](sprint-18-multi-step-orchestration.md).
- Can record accepted generated artifacts into the [Sprint 21 experience store](sprint-21-local-experience-store.md).
- Manager-side control-plane angle overlaps [`../../../javalens-manager/docs/sprints/sprint-future-networked-service.md`](../../../javalens-manager/docs/sprints/sprint-future-networked-service.md).
