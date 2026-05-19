# Sprint docs

Per the `~/CLAUDE.md` collaboration spec:

> We work agile in sprints. **Sprint docs** (in each repo under `docs/sprints/...`)
> are requirements + design + architecture. **Actionable plans** are sprint-doc
> derivatives that an agent can execute. They live under `~/.claude/plans/`.
> Do NOT conflate the two.

This directory is the home for `javalens-mcp` sprint docs (`sprint-N-backlog.md`,
spikes, design notes). It exists from v1.7.1 onward; earlier fork sprints
(Sprint 10 → 14, fork v1.3.0 → v1.7.1) were tracked without per-sprint docs.

## Where the fork's planning currently lives

Until proper sprint docs are written, the fork's forward plan is split across:

- [`../bugs.md`](../bugs.md) — living bug tracker. Open items as of v1.7.1:
  **#6** (`WorkspaceFileWatcher` doesn't reflect live `workspace.json` edits),
  **#7** (`add_project` on the fork's own multi-module repo — duplicate
  classpath entry).
- [`../upgrade-checklist.md`](../upgrade-checklist.md) — version-coupling
  checklist, **plus** the "Sprint 14 (v1.8.x) follow-up backlog" and
  "Agent feedback" sections that currently double as the de-facto backlog:
  - Gradle path for the Ring 3 dep tools (Buildship target addition).
  - Richer `find_unused_dependencies` via M2E classpath inspection.
  - `find_duplicate_code` tool (method-AST hash → token-stream).
  - Fixture-build pipeline — unblocks the `@Disabled` `run_tests` /
    `generate_test_skeleton` / `compile_workspace` happy-path tests.
  - Agent-precision levers: default-load the killer tools, dry-run mode on
    refactoring tools, structured `find_references` output, recommended-tools
    surface.
- [`../release-notes/`](../release-notes/) — per-release "what shipped" detail.

## Convention going forward

When the next fork sprint is planned, write `docs/sprints/sprint-15-backlog.md`
(requirements + design + architecture) here, and put the executable plan under
`~/.claude/plans/`. Migrate the relevant `upgrade-checklist.md` backlog prose
into that sprint doc so the checklist goes back to being purely a
target-platform-bump checklist.
