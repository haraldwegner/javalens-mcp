package org.javalens.mcp.transport;

import java.io.IOException;

/**
 * Transport seam between the MCP server's message loop and the underlying
 * I/O channel. Stdio (line-delimited over System.in/out) and HTTP (per-request
 * via servlet) both implement this interface.
 *
 * <p>Sprint 14a: introduced to open the seam for the HTTP/SSE transport
 * landing in v1.8.5. The stdio loop in {@code JavaLensApplication} previously
 * inlined the BufferedReader/PrintWriter wiring; this interface lets that
 * loop work against either transport without conditional branching.
 */
public interface Transport extends AutoCloseable {

    /**
     * Block until the next inbound JSON-RPC message is available, then
     * return it as a single line. Blank lines are skipped. Returns
     * {@code null} when the input stream is closed / EOF reached.
     */
    String readMessage() throws IOException;

    /**
     * Send a JSON-RPC response to the client. Implementations MUST flush
     * before returning so the message is visible to the peer immediately
     * (no buffering between the protocol handler and the wire).
     */
    void writeMessage(String message) throws IOException;

    @Override
    void close();
}
