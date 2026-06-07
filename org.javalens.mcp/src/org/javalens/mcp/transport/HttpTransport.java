package org.javalens.mcp.transport;

/**
 * HTTP transport stub — placeholder for Sprint 14a Stage 3.
 *
 * <p>Stage 2 wires CLI flag plumbing so the application's main loop can
 * select HTTP vs stdio. Constructing this class throws fast with a clear
 * message until Stage 3 lands the actual Jetty + {@code /mcp} POST +
 * SSE + Bearer-auth implementation.
 *
 * <p>This means the default-launch flow (no {@code -transport} flag)
 * intentionally errors out at v1.8.5 dev HEAD until Stage 3 completes.
 * Use {@code -transport stdio} as the opt-in fallback to test the
 * end-to-end loop against the existing protocol handler.
 */
public class HttpTransport implements Transport {

    public HttpTransport(int port, String bindAddress, String token) {
        throw new UnsupportedOperationException(
            "HTTP transport not yet implemented (Sprint 14a Stage 3). "
            + "Use -transport stdio as opt-in fallback until v1.8.5 ships.");
    }

    @Override
    public String readMessage() {
        throw new UnsupportedOperationException("HTTP transport stub");
    }

    @Override
    public void writeMessage(String message) {
        throw new UnsupportedOperationException("HTTP transport stub");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("HTTP transport stub");
    }
}
