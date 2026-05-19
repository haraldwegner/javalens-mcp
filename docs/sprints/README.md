# Sprint docs

Team collaboration convention:

> We work agile in sprints. **Sprint docs** (in each repo under `docs/sprints/...`)
> are requirements + design + architecture. **Actionable plans** are sprint-doc
> derivatives that an agent can execute; they're kept outside the repo.
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

## Numbering note

This fork has **no independent sprint numbering**. "Sprint N" is a
cross-repo unit owned by the `javalens-manager` side — historically a
sprint pairs a manager `v0.x` release with a fork `v1.y` release
(e.g. Sprint 12 = manager v0.12.0 + fork v1.6.0; Sprint 14 = fork
v1.7.1, manager packaging-only). Fork-only work is tracked by **fork
release version** (`v1.7.2`, `v1.8.0`, …), not a fork sprint number.

## Convention going forward

When a cross-repo sprint is planned, the manager side owns the sprint
number; the fork's slice of that sprint gets a doc here named for the
sprint (`sprint-N-backlog.md`) or, for fork-only arcs, for the fork
release (`v1.8.0-backlog.md`). Requirements + design + architecture go
in that doc; the executable step-by-step plan is kept outside the repo.
Migrate the relevant `upgrade-checklist.md` backlog prose into it so the
checklist returns to being purely a target-platform-bump checklist.
