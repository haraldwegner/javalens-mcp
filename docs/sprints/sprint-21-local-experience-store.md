# Fork Sprint 21 — Local experience knowledge store

> **Status: drafted 2026-06-21 from architecture discussion.** This is the first post-catalog sprint after Sprint 20's SOLID detection work. It records the local, lean knowledge-store decision before the product grows into a networked multi-user service.
>
> **Target version: goja-mcp v1.5**.
>
> **Predecessor:** [`sprint-20-solid-detection.md`](sprint-20-solid-detection.md) → goja-mcp v1.4.
>
> **Successor direction:** the eventual networked/shared-memory service captured in [`../../../javalens-manager/docs/sprints/sprint-future-networked-service.md`](../../../javalens-manager/docs/sprints/sprint-future-networked-service.md), plus the unified target-form catalog framework ([`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md)).

**Scaffold-level scope below; promote to actionable plan when Sprint 20 enters its cutover window.**

## Theme

Agents can inspect code with JDT and call refactoring tools, but they do not reliably remember a codebase's soft knowledge: business domain terms, architectural exceptions, failed refactor attempts, brittle tests, naming conventions, Javadoc-derived API contracts, project conventions, and lessons learned during previous work. Rules are too strict for this; free-form memory files are too passive; raw `grep`/`find`/`sed` is too low-level.

This sprint adds a **local experience knowledge store** to GOJA. It gives agents mandatory, situation-specific recall before they refactor Java code:

```
analyze_type / find_references / search_symbols
        +
get_naming_conventions / ingest_javadocs
        +
get_relevant_experience(symbol/package/operation)
        =
symbol-aware agent context before action
```

The store is local and embedded for v1.5. It is not the networked shared-memory service yet.

## Product roadmap fit

This sprint is the lean bridge across four product shapes:

1. **javalens/goja** — Java semantic engine + MCP tooling.
2. **Eclipse-integrated Java development agent** — GOJA pipes structured Java context through Claude CLI, Cursor CLI, Gemini CLI, Grok CLI, or similar agent runners.
3. **Eclipse-integrated domain agent** — the host product keeps application/business data in its own store; GOJA keeps agent knowledge in a separate embedded experience store.
4. **Full Java refactoring service** — local experience documents can migrate to a shared document-backed memory service when teams, CI agents, and hosted workspaces need shared recall.

The key decision: **keep local agent knowledge inside goja-mcp for now, behind a clean store interface.** Split it into a separate `goja-experience-mcp` only when the memory becomes shared, multi-user, cross-language, or hosted.

## Storage decision

Use **H2 embedded** as the local experience store.

Why H2:

- Pure Java and Eclipse friendly.
- Real database, not scattered files.
- No external daemon, Docker, Postgres, MongoDB, or FerretDB requirement.
- SQL indexes make retrieval by symbol, package, plugin, operation, status, confidence, and project-specific kind straightforward.
- Stores document-shaped JSON payloads in text/CLOB while indexing the important retrieval fields.
- Provides a clean boundary from host-product business data.
- Easy to export/import later into shared document collections.

## Boundary with host application data

Host products already have their own persistence story:

```
Host application
  application data store
    static domain data
    transaction/business data
    persisted platform state

GOJA agent sidecar
  H2
    observations
    lessons learned
    domain notes
    naming conventions
    Javadoc-derived contracts
    refactoring hazards
    project-specific playbooks
    agent run summaries
```

Do **not** mix agent experience into the host application's business store. Business data and agent knowledge have different correctness levels, audit expectations, backup policies, and lifecycle. Agent knowledge is advisory, evolving, sometimes wrong, and needs promotion/curation. Business data is authoritative.

## Candidate data model

Keep the payload document-shaped, but index the fields agents need for retrieval.

```
experience_entry
  id                 varchar primary key
  project_id         varchar
  product_context    varchar    -- goja, customer-x, product-y
  workspace_id       varchar
  plugin_id          varchar
  package_name       varchar
  symbol_fqn         varchar
  operation          varchar    -- dto_refactor, service_integration, lombok_migration, ...
  type               varchar    -- observation, lesson, naming_convention, api_contract, lifecycle_rule, project_rule, domain_fact, refactor_hazard
  status             varchar    -- candidate, accepted, rejected, superseded
  confidence         varchar    -- low, medium, high
  summary            varchar
  body_json          clob
  created_at         timestamp
  updated_at         timestamp
```

`body_json` preserves the future document-store shape:

```json
{
  "type": "project_lesson",
  "scope": {
    "plugin": "com.example.product",
    "packages": ["com.example.product.workflow"],
    "symbols": ["com.example.product.workflow.WorkflowCoordinator"]
  },
  "operations": ["service_integration", "eclipse_integration"],
  "summary": "Do not initialize product services before the workbench lifecycle is ready.",
  "details": "Several UI components assume OSGi services are available only after the platform startup sequence reaches the post-window phase.",
  "confidence": "medium",
  "status": "accepted",
  "evidence": {
    "files": ["plugin.xml"],
    "tests": [],
    "notes": ["Observed during agent-assisted design"]
  },
  "exceptions": [
    "Pure calculation components with no UI/service dependency can initialize earlier."
  ]
}
```

## Candidate tools

Keep the tool surface small and parametric:

- **`get_relevant_experience(query)`** — retrieve accepted lessons, hazards, naming conventions, Javadoc-derived API contracts, lifecycle rules, and domain facts by symbol/package/plugin/operation. This is the mandatory pre-refactor recall tool.
- **`record_observation(entry)`** — store an agent observation as `status=candidate`. Used after failed tests, surprising coupling, undocumented invariants, or project-specific findings.
- **`promote_experience(id, status, confidence?)`** — promote/reject/supersede candidate observations. For local use this can be agent-callable; for shared service it becomes admin/curation-gated.
- **`search_experience(query)`** — full-text-ish search over summaries and JSON payloads, backed initially by indexed fields plus simple text matching.
- **`export_experience(scope)`** — export accepted knowledge as document-shaped JSON for backup, sharing, or future service migration.
- **`import_experience(entries, mode)`** — import curated knowledge into the local store.

This tool family belongs inside GOJA while the store is local and Java-symbol-coupled. A future networked service can expose the same concepts from a separate experience MCP.

## Relationship to Sprint 15a naming/Javadocs

[`sprint-15a-naming-javadocs.md`](sprint-15a-naming-javadocs.md) adds active semantic tools for naming conventions and Javadocs. Sprint 21 is the persistence and curation layer for their durable outputs:

- `infer_naming_conventions` returns observed conventions; Sprint 21 stores accepted or rejected convention entries with scope, confidence, examples, and exceptions.
- `get_naming_conventions` can read live inference results first and accepted experience entries second.
- `suggest_name` and `check_name_against_conventions` can consult accepted naming entries before proposing new symbols or validating generated code.
- `ingest_javadocs` extracts symbol-anchored facts; Sprint 21 stores accepted `api_contract`, `lifecycle_rule`, `deprecated_behavior`, `extension_point`, and `domain_fact` entries.
- `generate_javadocs` can use accepted experience entries as context before writing or improving comments.
- `validate_javadocs` can emit candidate observations when public API docs are missing, stale, broken, or contradictory.

The source of truth for Javadocs remains the Java source file. The experience store keeps retrievable, symbol-anchored knowledge derived from those comments, plus curation status and evidence.

## Retrieval workflow

The important rule is mandatory retrieval, not mandatory obedience.

Before changing Java code, an agent should:

1. Resolve the code target with GOJA/JDT (`analyze_type`, `find_references`, `search_symbols`, etc.).
2. Call `get_relevant_experience` with the resolved symbol/package/plugin and intended operation.
3. Treat returned entries as weighted advice:
   - `status=accepted` + `confidence=high` → strong warning.
   - `confidence=medium` → inspect evidence and exceptions.
   - `status=candidate` → useful hint, not policy.
4. Record new observations after failed tests, surprising compile errors, or discovered invariants.

Examples:

- "Keep no-arg constructors on billing DTOs; legacy XML/Jackson tests depend on them."
- "In Eclipse-based product plugins, avoid early service lookup before the workbench lifecycle is stable."
- "Lombok removal is safe for internal data holders but not for API DTOs serialized by partner integrations."
- "Outbound integration adapters use the suffix `Client`; `Service` is reserved for business services."
- "The Javadoc on this public method defines a rounding invariant that must survive extraction or modernization."

## Alternatives considered

| Option | Decision |
|---|---|
| Markdown / JSONL files | Rejected for this sprint. Too passive, too easy for agents to ignore, and not a "real store." Still useful as export format. |
| Host application store | Good fit for application state, but not the best boundary for advisory agent knowledge. Mixing with business data would blur lifecycle and authority. |
| H2 embedded | **Chosen**. Lean, Java-native, SQL-indexable, easy to bundle, easy future export to document-shaped entries. |
| MapDB | Plausible ultra-light key/value fallback, but weaker ad hoc querying than H2. |
| QuestDB | Good for agent telemetry and run analytics, not primary curated knowledge. |
| PostgreSQL JSONB + pgvector | Strong prototype for a service, but too heavy for the local embedded-product stage. |
| FerretDB | Useful only if MongoDB API compatibility must be proven from day one. Not needed for the lean local stage. |
| MongoDB Atlas | Possible future shared-memory backend; too heavy and network-shaped for the local GOJA prototype. |

## Future shared-memory backend path

The local H2 store should not depend on any hosted database. It should preserve a document-shaped payload so accepted knowledge can migrate later to a shared backend if GOJA grows beyond local single-user use. MongoDB Atlas is one candidate because it combines document storage with search/vector capabilities, but the Sprint 21 design must stay backend-neutral.

GOJA's differentiator is not generic memory storage. It is **Java-symbol-aware experience** grounded in JDT:

- symbols
- packages
- plugins/bundles
- tests
- commits
- refactoring operation types
- modernization catalogs
- naming conventions
- Javadoc-derived API contracts
- project-specific playbooks

The shared-service version, whether MongoDB-backed or implemented on another document/search store, should preserve the same domain model:

> A shared backend provides durable team memory. GOJA provides the Java/codebase cognition layer that knows when a memory is relevant to a symbol, package, test, or refactoring operation.

## Dependencies

- Builds on Sprint 16 GOJA rebrand and tool consolidation.
- Consumes durable outputs from Sprint 15a naming/Javadoc tools.
- Benefits from Sprint 17-20 detection catalogs because experience entries can refer to catalog/operation kinds.
- Does not require the networked-service sprint.
- Does not require a separate manager UI in v1.5.

## Acceptance signal

- H2-backed `ExperienceStore` interface implemented under GOJA.
- Local store path is workspace-scoped and does not mix with host-application business data.
- `get_relevant_experience`, `record_observation`, and one curation operation are available through MCP.
- Accepted naming conventions and Javadoc-derived contracts can be stored, queried, promoted, rejected, exported, and imported.
- At least one focused fixture proves symbol/package/operation retrieval.
- End-to-end smoke: agent resolves a Java symbol, asks for relevant experience, receives a matching accepted lesson plus an applicable naming or Javadoc-derived entry, records a new candidate observation, then retrieves it by operation.
- Export produces document-shaped JSON entries for accepted lessons.
- Full reactor `mvn clean verify` green.

## Source planning notes

- Architecture discussion, 2026-06-21: local H2 for GOJA experience knowledge; document/search backend later for shared enterprise memory.
- Sprint 15a: [`sprint-15a-naming-javadocs.md`](sprint-15a-naming-javadocs.md), which produces naming convention and Javadoc-derived knowledge for Sprint 21 to persist.
- Manager networked-service outlook: [`../../../javalens-manager/docs/sprints/sprint-future-networked-service.md`](../../../javalens-manager/docs/sprints/sprint-future-networked-service.md).
- Forward link: [`sprint-future-target-form-catalogs.md`](sprint-future-target-form-catalogs.md), because target-form catalogs provide natural `operation` and `catalog/kind` anchors for experience entries.
