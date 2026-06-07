# Fork Sprint 14a — HTTP/SSE transport (DEFAULT)

> **Status: re-scoped 2026-06-07.** Originally framed as additive opt-in (stdio default, HTTP/SSE opt-in via flag). Re-scoped after the 30 GB JVM leak (manager bug #9) was elevated to Priority 0: HTTP/SSE becomes the **default** transport in v1.8.5; stdio remains supported as an opt-in fallback via `-transport stdio` so existing v1.8.0 setups don't break.
>
> **Target version: javalens-mcp v1.8.5.** Patch bump — additive at the protocol level. Default-transport flip is documented as a behavior change in release notes; users who pin to stdio explicitly are unaffected.
>
> **Predecessor:** [`sprint-14-backlog.md`](sprint-14-backlog.md) → v1.8.0 (shipped 2026-06-04, `ffa68c7`).
>
> **Coupled release pair:** manager [`sprint-15-backlog.md`](../../../javalens-manager/docs/sprints/sprint-15-backlog.md) → v0.15.0 ships alongside this release. Manager Sprint 15 absorbs the resident-JVM hosting + URL-emitting MCP writer that close bug #9 end-to-end; v1.8.5 is the foundation it consumes.
>
> **Sequencing:** Sprint 14a (this) → v1.8.5 → Sprint 14b (full-apply + upstream pzalutski v1.3.5 parity sync) → v1.9.0 → Sprint 15 (modernisation sweeps) → v1.10.0 → Sprint 16 (GOJA rebrand at goja-mcp v1.0).

## Goal

Ship **javalens-mcp v1.8.5** with HTTP/SSE as the **default** transport — one long-running JVM per workspace serving N MCP clients via HTTP. Stdio remains available via `-transport stdio` for users who explicitly pin to it.

This unlocks the **"one resident JVM per workspace"** model that the manager consumes in Sprint 15: N clients × M workspaces → **M JVMs total**, not N × M.

## Motivations (re-ordered 2026-06-07)

1. **PRIMARY — Manager bug #9: 30 GB JVM leak.** Today every MCP client process spawns its own pair of stdio JVMs per deployed workspace. Observed: 8 client processes × 2 workspaces = 16 JVMs at ~24 GB RAM. HTTP/SSE shared service closes this end-to-end with manager Sprint 15.

2. **Multi-client lock contention.** A containerized agent targeting the same `-data` dir as a running Claude hangs forever on the Eclipse JDT workspace lock. HTTP/SSE with one resident JVM per workspace eliminates the contention because all clients route through the same process.

3. **Antigravity MCP-orphan parallel (verified 2026-06-07).** Web check resolved the prior "nsjail blocks fork+exec" theory: nsjail is still the Antigravity Linux sandbox but it does NOT structurally block stdio MCP subprocesses (verified via Google's developer forum + recent Antigravity 2.0 docs — subprocess spawn works). The real and well-documented Antigravity issue is that **spawned MCP server processes are orphaned at conversation end and accumulate until system RAM exhausts** ([discuss.ai.google.dev #139866](https://discuss.ai.google.dev/t/bug-all-mcp-server-processes-are-orphaned-after-conversation-ends-accumulate-indefinitely-causing-system-memory-exhaustion/139866)). That is the same leak class as our bug #9, surfacing at the IDE level rather than the manager level. HTTP/SSE shared service dodges this end-to-end: the resident JVM lives across conversations, lifecycle is the manager's not the IDE's, and conversation-end orphaning has no JVM to leak. Stdio mode in Antigravity also has the additional fragility that any debug write to stdout corrupts the JSON-RPC framing — HTTP cleanly separates protocol from logs.

## Strategic positioning (USP vs upstream Pzalutski-pixel/javalens-mcp)

Upstream at v1.3.5 is stdio-only. Multi-client shared service over HTTP/SSE is a capability gap stdio fundamentally cannot close — its "one JVM per client process" is structural, not a config knob. v1.8.5 establishes the fork as the only JDT-accurate MCP server that scales to N agents per workspace without N× JVM cost. Feature in release notes as a real capability, not just a transport choice.

## Requirements

1. **HTTP listener embedded in the MCP server.** Embed Jetty in OSGi. Bind to `127.0.0.1` only by default for security. Configurable via `-port N -bind 127.0.0.1` CLI flags. Multiple workspaces = multiple ports (one JVM per workspace per the manager's per-workspace lifecycle).

2. **SSE channel for server-pushed notifications.** Tool-output events (progress, partial results, completion) push from server to client via `GET /mcp/events` (Server-Sent Events).

3. **MCP protocol over HTTP.** JSON-RPC framing javalens already speaks works unchanged; the change is transport substitution. Request → `POST /mcp` with JSON-RPC body; response → JSON-RPC response. Server-push → SSE event on `GET /mcp/events`.

4. **Bearer-token auth.** Per-session token in `Authorization: Bearer <token>` headers. Generated at server startup via `SecureRandom` (32-byte hex) if not provided via `-token T` / `-token-file <path>`. Localhost binding + token = adequate for the single-user-workstation case.

5. **Transport selection at startup.** Default = HTTP/SSE. New CLI flag `-transport stdio` opts back to stdio for users who pin to v1.8.0 behavior. Server bootstrap branches on the flag; everything below the I/O layer is identical.

6. **Manager-capture contract (READY line).** After successful HTTP bind, server prints exactly one line to stdout: `READY url=http://127.0.0.1:<port> token=<token>`. Token MUST appear ONLY on the READY line; nowhere else on stdout (manager may capture stdout into logs). The manager parses this line to wire deployed MCP-config entries. This contract is asserted by JUnit tests on the fork side AND by Rust parser tests on the manager side.

7. **Auto-port-allocation.** When `-port` is absent, bind to an ephemeral port (port 0 → OS-assigned), capture the bound port, emit it in the READY line. Allows the manager to pre-assign a stable port from its allocator (Stage 9 of the coupled plan) OR let the OS assign on each launch.

## Migration

- **HTTP/SSE is the new default.** Documented behavior change in v1.8.5 release notes. Users who upgrade from v1.8.0 and don't pass any flag will get an HTTP listener instead of an stdio loop.
- **Stdio opt-in via `-transport stdio`.** Users who pinned to v1.8.0 stdio behavior keep it working by adding the flag to their launch args. Manager v0.14.x (pre-Sprint-15) users who upgrade the fork without upgrading the manager will have broken stdio MCP entries — they should upgrade both to v1.8.5 + v0.15.0 as a release pair.
- **Forward-compatible with the multi-user networked service vision** ([`../../../javalens-manager/docs/sprints/sprint-future-networked-service.md`](../../../javalens-manager/docs/sprints/sprint-future-networked-service.md)). v1.8.5 ships the local-only HTTP layer; the future networked-service sprint adds TLS, OAuth, per-user ACL, audit logs on top of the same transport substrate.

## Repos touched

- **`javalens-mcp`** — this sprint.
- **`javalens-manager`** — coupled release in Sprint 15 → v0.15.0 (consumes v1.8.5: spawns resident JVMs per workspace, parses READY line, emits URL endpoints in deployed MCP-client configs, honors `autostart_on_boot`).

## Out of scope (settled)

- **TLS / multi-tenant auth / OAuth / per-user ACLs** — networked service sprint (`sprint-future-networked-service.md`). v1.8.5 ships local-only HTTP with single-token auth.
- **Removal of stdio support** — stdio stays supported via opt-in for the foreseeable future. Removed only if/when the network service vision matures.
- **The 18 sprints' worth of tool-surface content** (Fowler smells, modernisation, Kerievsky, SOLID) — separate sprints, independent of transport choice.

## Authorship / attribution rule

No Claude / AI / "Generated by …" attribution anywhere — commit messages, PR bodies, release notes, code comments, docs. Same rule that's held since 2026-04-27.

## Order of work (suggested)

1. **Transport-shim extract.** Pull the stdio I/O loop out of `JavaLensApplication.main()` into a `Transport` interface + `StdioTransport` impl. Zero observable change. Sets the seam for HTTP.
2. **CLI flag plumbing.** Parse `-transport stdio|http` (HTTP DEFAULT), `-port N`, `-bind 127.0.0.1`, `-token T`, `-token-file <path>`. HTTP branch constructs a stub HttpTransport.
3. **Jetty bootstrap + `/mcp` POST + READY emission + Bearer auth.** Core stage. Jetty binds to `bind:port`; servlet at `/mcp` parses JSON-RPC, dispatches via `McpProtocolHandler`, writes response. After bind, print `READY url=... token=...` to stdout. Bearer-token auth on every request; 401 on missing/wrong. Sub-step: target-platform bump if Jetty bundle absent.
4. **SSE channel `/mcp/events`.** Long-lived GET (with `Authorization: Bearer <token>`) streams `event: <kind>\ndata: <json>\n\n` per tool-output event. Heartbeat every 30 s.
5. **Stdio regression: full reactor.** `mvn clean verify` — existing stdio MCP integration tests stay GREEN (proves the transport extract + flag plumbing didn't regress the opt-in fallback).
6. **Real-client E2E + sandbox repro (status TBD).** Configure Claude Desktop / Cursor with HTTP MCP entry → connect → tool round-trip. If nsjail still applies, repro the sandboxed-agent connection.
7. **Release v1.8.5.** 4 manifests + 8 poms + product UID `1.8.0.qualifier` → `1.8.5.qualifier`. Release notes covering: default-transport flip, READY line + token contract, USP vs upstream, bug #9 leak fix coordinated with manager v0.15.0.

## Critical files

- **Bootstrap:** `org.javalens.mcp/src/org/javalens/mcp/JavaLensApplication.java` (current stdio I/O loop at line 449) — extract Transport seam + parse CLI flags.
- **MCP protocol handler:** `org.javalens.mcp/src/org/javalens/mcp/protocol/McpProtocolHandler.java` — UNCHANGED; transport-agnostic JSON-RPC dispatch already exists. Just rewired to the Transport seam.
- **New transport package:** `org.javalens.mcp/src/org/javalens/mcp/transport/` — `Transport.java` interface + `StdioTransport.java` + `HttpTransport.java`.
- **Manifest:** `org.javalens.mcp/META-INF/MANIFEST.MF` — `Require-Bundle: org.eclipse.jetty.server, org.eclipse.jetty.servlet`.
- **Target platform:** `org.javalens.target/org.javalens.target.target` — pre-flight check at Stage -1; bump if Jetty bundle absent (sub-step inside the Jetty bootstrap stage).

## Reusable infrastructure already in place

- **MCP protocol handler** — JSON-RPC framing is transport-agnostic. The existing handler just gets a Transport-shim adapter instead of `System.in`/`System.out`.
- **Existing tool implementations** — all 75 tools work unchanged. They receive `ToolRequest` and return `ToolResponse`; they don't know about transport.

## Verification (sprint exit)

- **HTTP transport functional smoke:** `curl -X POST http://127.0.0.1:<port>/mcp -H 'Authorization: Bearer <token>' -d '...'` round-trip works (health_check, list_projects, search_symbols).
- **SSE smoke:** `curl -N -H 'Authorization: Bearer <token>' http://127.0.0.1:<port>/mcp/events` shows server-push events flowing during a long-running tool call (e.g. `compile_workspace`).
- **Stdio regression smoke:** all existing stdio MCP integration tests stay GREEN under `-transport stdio`.
- **Real-client end-to-end:** Claude Desktop / Cursor configured with HTTP-transport entry connects, calls tools, refactoring round-trip works.
- **READY line + token contract:** `readyLineEmittedToStdout` + `tokenNotInStdoutBeyondReadyLine` JUnit tests pin the format both fork-side and (via the manager's parser test in Sprint 15) manager-side.
- **Manager Sprint 15 joint smoke:** with v1.8.5 + v0.15.0 running, opening 3 Claudes + 1 Cursor → `pgrep -af javalens.jar | wc -l` returns **2** (one per workspace) not **16** (clients × workspaces). This is the end-to-end bug-#9 closure acceptance signal.

## Cut lines (if a stage hits unexpected pain)

- **SSE channel** — first cut. Pure request-response over HTTP without SSE still closes bug #9 (one JVM per workspace, shared across clients). SSE adds streaming progress for long-running tools. Acceptable to defer to v1.8.6 if Jetty-SSE quirks surface.
- **Token auth** — second cut. Localhost-only binding already prevents external access; per-session tokens are belt-and-braces. Token auth is needed for multi-process coordination on shared hosts (manager Sprint 15 passes the token through), so cutting here means the manager generates a token but the fork accepts any. Document as v1.8.6.
- **Real-client sandbox repro (6b)** — third cut. If nsjail is resolved or the sandbox environment is unavailable, skip; document the skip in the commit message.

## Build / test commands

```bash
cd /home/harald/CursorProjects/javalens-mcp
mvn clean verify                                 # full reactor (~20 min)
mvn -pl org.javalens.mcp.tests -am -Dtest='HttpTransport*Test' verify   # focused
mvn -pl org.javalens.mcp.tests -am -Dtest='McpProtocolHandlerTest' verify   # regression on transport-agnostic handler
java -jar org.javalens.product/target/products/javalens.jar -data /tmp/test-workspace
# Captures READY line: READY url=http://127.0.0.1:<port> token=<token>
curl -X POST http://127.0.0.1:<port>/mcp -H 'Authorization: Bearer <token>' -d '{"jsonrpc":"2.0","method":"health_check","id":1}'
```

## Definition of Done

- [ ] Default `java -jar ... -data <ws>` launches an HTTP server. `curl` round-trip works with Bearer auth.
- [ ] `-transport stdio` opts back to stdio (regression-tested via the full reactor).
- [ ] READY line + token contract honored (`readyLineEmittedToStdout` + `tokenNotInStdoutBeyondReadyLine` GREEN).
- [ ] SSE channel pushes tool-output events (or cut-line noted in release notes).
- [ ] Bearer auth gates non-authorized requests with 401 (or cut-line noted in release notes).
- [ ] End-to-end smoke: Claude Desktop / Cursor with HTTP entry connects and runs a refactoring round-trip.
- [ ] Sandboxed-agent reproduction connects over HTTP (or skipped with reason if nsjail no longer applies).
- [ ] `docs/release-notes/v1.8.5.md` written, covering default-transport flip + USP framing + bug #9 leak fix coordinated with manager v0.15.0.
- [ ] Bundle-Version + pom versions bumped `1.8.0.qualifier` → `1.8.5.qualifier` across 4 manifests + 8 poms + product.
- [ ] Tag `v1.8.5` pushed; CI publishes the GitHub Release as Latest.
- [ ] No AI-attribution boilerplate anywhere.
- [ ] Memory updated: `project_sprint_state.md` → "fork Sprint 14a closed, v1.8.5 shipped; HTTP/SSE is default; manager Sprint 15 ships coupled v0.15.0 with bug #9 end-to-end closure".
