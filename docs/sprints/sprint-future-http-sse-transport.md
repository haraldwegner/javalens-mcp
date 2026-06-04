# Fork Sprint (SCAFFOLD) — HTTP/SSE transport

**Status:** scaffold only. Theme + summary + candidate items. Not an actionable plan yet.

**Surfaced 2026-05-17** during EXECSIM Session 2: sandboxed-agent stdio spawn breakage. Sequencing TBD — likely Sprint 16+ because it touches the MCP-server's I/O layer (not a small refactor), but the user-pain rationale is real.

## Theme

JavaLens currently exposes MCP over stdio: the agent's MCP client spawns the JavaLens process, talks JSON-RPC over stdin/stdout. Stdio works great when the client owns the spawn — but breaks when the client is sandboxed (Antigravity's tool-call sandbox; Cursor's container model in some configs) because the sandbox can't open arbitrary processes.

HTTP/SSE (Server-Sent Events) transport sidesteps the sandbox: the client connects to a long-running JavaLens process via HTTP (request-response) + SSE (server-pushed notifications). No spawn, no sandboxing conflict.

## Candidate items

- **HTTP listener** — embed a small HTTP server (Eclipse already ships Jetty in the Equinox runtime; reuse). Bind to localhost only by default.
- **SSE channel** — push tool-output events back to the client. SSE is well-supported in browsers and easy to consume in MCP-client libraries.
- **MCP protocol over HTTP** — the JSON-RPC framing JavaLens already speaks works unchanged; just transport-substituted.
- **Auth layer (minimal)** — at minimum, a per-session token in `Authorization` headers. Localhost-only listener + tokens covers the common case; future TLS/proxy layer if remote use surfaces.
- **Transport selection at startup** — `-transport stdio` (default) vs `-transport http -port N -token T`. Manager surfaces the choice in System Settings.

## Migration

- Existing stdio clients work unchanged — stdio stays the default.
- HTTP/SSE is opt-in via command-line flag. No behaviour change for current users.
- Manager (and future MCP-client integrations) gain a transport setting per service.

## Dependencies

- Independent of Sprints 14-19 (transport-layer, not tool-surface).
- Touches the MCP-server I/O layer — careful scope to avoid concurrent edits with other sprints that also touch the server bootstrap.
- Requires manager-side update to consume the new transport (small — separate UI checkbox in System Settings).

## Acceptance signal

- `-transport http -port N -token T` launches a working server.
- End-to-end smoke: manager configured with HTTP transport → connects → `health_check` returns expected data → a refactoring round-trip succeeds.
- Stdio smoke unchanged — regression-free.
- Sandboxed-agent reproduction (EXECSIM Session 2) — confirm sandbox can connect over HTTP where it couldn't spawn over stdio.

## Source planning notes

- 2026-05-17 EXECSIM Session 2 report (in upgrade-checklist or session memory).
- Currently the v1.7.0 line ships stdio only.
- This is the highest-priority unblock for sandboxed-agent users; consider pulling forward if a user hits the wall.
