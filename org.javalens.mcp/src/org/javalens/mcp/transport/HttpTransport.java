package org.javalens.mcp.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * HTTP transport using the JDK's built-in
 * {@link com.sun.net.httpserver.HttpServer}.
 *
 * <p>Sprint 14a Stage 3 chose the JDK server over an embedded Jetty bundle
 * because the Eclipse target platform did not already include Jetty
 * (pre-flight grep returned empty). Adding Jetty would require a target
 * platform bump plus the Plan-agent-flagged risk of OSGi class-loader vs
 * servlet-thread friction with JDT APIs. The JDK server avoids all of
 * that: no target changes, no new bundle dependencies, no OSGi
 * class-loader concerns, works guaranteed with Java 21.
 *
 * <p><b>Endpoints (Stage 3):</b>
 * <ul>
 *   <li>{@code POST /mcp} — request-response. Body is a JSON-RPC message;
 *       returns the JSON-RPC response (200 + application/json) or 204
 *       (notification). 400 on malformed JSON, 401 on missing/wrong
 *       Bearer token, 405 on non-POST.</li>
 * </ul>
 * Stage 4 adds {@code GET /mcp/events} (SSE).
 *
 * <p><b>READY contract:</b> after a successful bind, exactly one line is
 * written to stdout: {@code READY url=http://<bind>:<port> token=<token>}.
 * The token MUST NOT appear elsewhere on stdout — the manager-side
 * launcher captures stdout into a log file, and a leaked token in logs is
 * a security regression. See {@code tokenNotInStdoutBeyondReadyLine} test.
 *
 * <p><b>Bearer auth:</b> every request must carry
 * {@code Authorization: Bearer <token>}; missing or wrong → 401 with
 * empty body. Localhost binding is the secondary gate.
 */
public class HttpTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(HttpTransport.class);

    private final int requestedPort;
    private final String bindAddress;
    private final String token;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private final CountDownLatch readyLatch = new CountDownLatch(1);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private volatile int actualPort = -1;

    public HttpTransport(int port, String bindAddress, String token) {
        if (bindAddress == null || bindAddress.isBlank()) {
            throw new IllegalArgumentException("bindAddress required");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token required (use TokenGenerator.generate() if not provided)");
        }
        this.requestedPort = port;
        this.bindAddress = bindAddress;
        this.token = token;
    }

    /**
     * Start the HTTP listener, emit READY on stdout, then block until
     * {@link #close} is invoked from another thread.
     *
     * <p>If {@code requestedPort} was 0 (auto-allocate), the OS-assigned
     * port is captured and exposed via the READY line + {@link #getActualPort}.
     */
    @Override
    public void run(MessageHandler handler) throws IOException, InterruptedException {
        if (handler == null) {
            throw new IllegalArgumentException("handler required");
        }
        server = HttpServer.create(new InetSocketAddress(bindAddress, requestedPort), 0);
        server.createContext("/mcp", new McpHandler(handler, token, objectMapper));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        actualPort = server.getAddress().getPort();
        // READY contract: single line on stdout, token appears here ONLY.
        System.out.println("READY url=http://" + bindAddress + ":" + actualPort + " token=" + token);
        readyLatch.countDown();

        log.info("HTTP transport listening on {}:{}", bindAddress, actualPort);

        shutdownLatch.await();
        log.info("HTTP transport shutdown signal received");
    }

    /**
     * Returns the OS-assigned port once {@link #run} has bound the
     * listener. Used by tests and by the manager's READY-line capture.
     * Blocks until the server is ready (throws if interrupted).
     */
    public int getActualPort() throws InterruptedException {
        readyLatch.await();
        return actualPort;
    }

    @Override
    public void close() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (Exception e) {
                log.warn("Error stopping HTTP server: {}", e.getMessage());
            }
            server = null;
        }
        shutdownLatch.countDown();
    }

    /**
     * Translates HTTP requests on {@code /mcp} into JSON-RPC dispatch via
     * the application-supplied {@link MessageHandler}. Enforces Bearer
     * auth + basic JSON syntactic validation up-front.
     */
    private static final class McpHandler implements HttpHandler {

        private final MessageHandler handler;
        private final String expectedAuthHeader;
        private final ObjectMapper objectMapper;

        McpHandler(MessageHandler handler, String token, ObjectMapper objectMapper) {
            this.handler = handler;
            this.expectedAuthHeader = "Bearer " + token;
            this.objectMapper = objectMapper;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendEmpty(exchange, 405);
                    return;
                }

                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                if (auth == null || !auth.equals(expectedAuthHeader)) {
                    sendEmpty(exchange, 401);
                    return;
                }

                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                if (bodyBytes.length == 0) {
                    sendEmpty(exchange, 400);
                    return;
                }
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                if (body.isBlank()) {
                    sendEmpty(exchange, 400);
                    return;
                }

                // Syntactic JSON check up-front so the dispatcher only sees
                // well-formed messages. McpProtocolHandler does its own
                // semantic JSON-RPC validation downstream.
                try {
                    objectMapper.readTree(body);
                } catch (JsonProcessingException e) {
                    sendEmpty(exchange, 400);
                    return;
                }

                String response = handler.handle(body);
                if (response != null) {
                    byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, respBytes.length);
                    exchange.getResponseBody().write(respBytes);
                } else {
                    // Notification — no response body per JSON-RPC.
                    exchange.sendResponseHeaders(204, -1);
                }
            } catch (Exception e) {
                log.error("HTTP dispatch error", e);
                try {
                    sendEmpty(exchange, 500);
                } catch (IOException ignored) {
                    // already failed; drop
                }
            } finally {
                exchange.close();
            }
        }

        private static void sendEmpty(HttpExchange exchange, int status) throws IOException {
            exchange.sendResponseHeaders(status, -1);
        }
    }
}
