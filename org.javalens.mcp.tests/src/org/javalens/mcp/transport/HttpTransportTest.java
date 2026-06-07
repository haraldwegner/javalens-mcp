package org.javalens.mcp.transport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 14a Stage 3: HttpTransport round-trip + auth + READY contract tests.
 *
 * <p>Uses the JDK's built-in HTTP client + {@link HttpTransport.Transport.MessageHandler}
 * stubs. Each test starts the transport on an ephemeral port via the
 * {@link TestServer} helper which also captures System.out so the READY
 * line and the token-not-leaked-beyond-READY contract can be verified.
 */
class HttpTransportTest {

    private static final String TOKEN = "test-token-deadbeef-cafef00d";

    @Test
    @DisplayName("respondsToHealthCheckOverHttp — POST /mcp dispatches and returns 200")
    void respondsToHealthCheckOverHttp() throws Exception {
        try (TestServer server = new TestServer(msg ->
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}")) {
            HttpResponse<String> resp = post(server.port(), TOKEN,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"health_check\"}");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("\"ok\":true"),
                "expected handler response body, got: " + resp.body());
            assertEquals("application/json",
                resp.headers().firstValue("Content-Type").orElse(null),
                "response should be tagged application/json");
        }
    }

    @Test
    @DisplayName("handlerReceivesInboundBody — verifies the raw JSON-RPC reaches the dispatcher")
    void handlerReceivesInboundBody() throws Exception {
        final String[] capturedBody = new String[1];
        try (TestServer server = new TestServer(msg -> {
            capturedBody[0] = msg;
            return "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":1}";
        })) {
            String body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"echo\",\"params\":{\"x\":42}}";
            HttpResponse<String> resp = post(server.port(), TOKEN, body);
            assertEquals(200, resp.statusCode());
            assertEquals(body, capturedBody[0]);
        }
    }

    @Test
    @DisplayName("missingTokenReturns401 — POST without Authorization → 401, empty body")
    void missingTokenReturns401() throws Exception {
        try (TestServer server = new TestServer(msg -> {
            throw new AssertionError("handler should not be invoked when auth fails");
        })) {
            HttpResponse<String> resp = postNoAuth(server.port(),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"x\"}");
            assertEquals(401, resp.statusCode());
            assertTrue(resp.body().isEmpty(),
                "401 body should be empty, got: " + resp.body());
        }
    }

    @Test
    @DisplayName("wrongTokenReturns401")
    void wrongTokenReturns401() throws Exception {
        try (TestServer server = new TestServer(msg -> {
            throw new AssertionError("handler should not be invoked when auth fails");
        })) {
            HttpResponse<String> resp = post(server.port(), "not-the-real-token",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"x\"}");
            assertEquals(401, resp.statusCode());
        }
    }

    @Test
    @DisplayName("malformedJsonReturns400 — bad body → 400")
    void malformedJsonReturns400() throws Exception {
        try (TestServer server = new TestServer(msg -> {
            throw new AssertionError("handler should not be invoked on bad JSON");
        })) {
            HttpResponse<String> resp = post(server.port(), TOKEN, "this { is not json");
            assertEquals(400, resp.statusCode());
        }
    }

    @Test
    @DisplayName("emptyBodyReturns400")
    void emptyBodyReturns400() throws Exception {
        try (TestServer server = new TestServer(msg -> {
            throw new AssertionError("handler should not be invoked on empty body");
        })) {
            HttpResponse<String> resp = post(server.port(), TOKEN, "");
            assertEquals(400, resp.statusCode());
        }
    }

    @Test
    @DisplayName("nonPostMethodReturns405 — GET /mcp → 405")
    void nonPostMethodReturns405() throws Exception {
        try (TestServer server = new TestServer(msg -> "")) {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.port() + "/mcp"))
                .header("Authorization", "Bearer " + TOKEN)
                .GET()
                .build();
            HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(405, resp.statusCode());
        }
    }

    @Test
    @DisplayName("notificationReturns204 — handler returns null → 204 (no content)")
    void notificationReturns204() throws Exception {
        try (TestServer server = new TestServer(msg -> null)) {
            HttpResponse<String> resp = post(server.port(), TOKEN,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\"}");
            assertEquals(204, resp.statusCode());
        }
    }

    @Test
    @DisplayName("autoPortAllocatedWhenZero — requesting port=0 binds ephemeral OS-assigned")
    void autoPortAllocatedWhenZero() throws Exception {
        try (TestServer server = new TestServer(msg -> "")) {
            int p = server.port();
            assertTrue(p > 0 && p < 65536,
                "expected OS-assigned ephemeral port, got: " + p);
            // Ephemeral range is conventionally 32768-60999 on Linux but can vary;
            // just sanity-check it's a usable, non-zero port.
            assertNotEquals(0, p);
        }
    }

    @Test
    @DisplayName("readyLineEmittedToStdout — exactly one READY url=... token=... line")
    void readyLineEmittedToStdout() throws Exception {
        try (TestServer server = new TestServer(msg -> "")) {
            int p = server.port();
            String captured = server.capturedStdout();
            String expected = "READY url=http://127.0.0.1:" + p + " token=" + TOKEN;
            assertTrue(captured.contains(expected),
                "expected READY line in stdout, got: [" + captured + "]");
            // Exactly one occurrence of "READY "
            int count = captured.split("READY ", -1).length - 1;
            assertEquals(1, count,
                "expected exactly one READY occurrence, got " + count + ": " + captured);
        }
    }

    @Test
    @DisplayName("tokenNotInStdoutBeyondReadyLine — security: no leak elsewhere on stdout")
    void tokenNotInStdoutBeyondReadyLine() throws Exception {
        try (TestServer server = new TestServer(msg -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}")) {
            // Exercise a few requests so dispatch paths run.
            post(server.port(), TOKEN, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"a\"}");
            post(server.port(), TOKEN, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"b\"}");
            post(server.port(), "wrong", "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"c\"}");

            String captured = server.capturedStdout();
            String afterReady = captured.replaceFirst(
                "READY url=\\S+ token=\\S+\\R?", "");
            assertFalse(afterReady.contains(TOKEN),
                "token must not appear in stdout beyond the READY line. "
                + "After-ready content: [" + afterReady + "]");
        }
    }

    @Test
    @DisplayName("rejectsNullBindAddress / rejectsNullToken at construction")
    void rejectsNullBindAndToken() {
        assertThrows(IllegalArgumentException.class,
            () -> new HttpTransport(0, null, "tok"));
        assertThrows(IllegalArgumentException.class,
            () -> new HttpTransport(0, "127.0.0.1", null));
        assertThrows(IllegalArgumentException.class,
            () -> new HttpTransport(0, "127.0.0.1", ""));
    }

    // ===== Helpers =====

    private static HttpResponse<String> post(int port, String token, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        return HttpClient.newHttpClient()
            .send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> postNoAuth(int port, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        return HttpClient.newHttpClient()
            .send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Boots an HttpTransport on a separate thread bound to an ephemeral port,
     * captures System.out for the READY-line + token-no-leak assertions, and
     * restores stdout on close.
     */
    static final class TestServer implements AutoCloseable {
        final HttpTransport transport;
        final Thread runThread;
        final ByteArrayOutputStream stdoutCapture;
        final PrintStream originalStdout;
        final int port;

        TestServer(Transport.MessageHandler handler) throws Exception {
            this.originalStdout = System.out;
            this.stdoutCapture = new ByteArrayOutputStream();
            System.setOut(new PrintStream(stdoutCapture, true, StandardCharsets.UTF_8));

            this.transport = new HttpTransport(0, "127.0.0.1", TOKEN);
            this.runThread = new Thread(() -> {
                try {
                    transport.run(handler);
                } catch (Exception ignored) {
                    // run() returns on close; swallowing is fine.
                }
            }, "HttpTransportTest-runner");
            runThread.setDaemon(true);
            runThread.start();

            this.port = transport.getActualPort();
        }

        int port() {
            return port;
        }

        String capturedStdout() {
            return stdoutCapture.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws InterruptedException {
            transport.close();
            runThread.join(5000);
            System.setOut(originalStdout);
        }
    }
}
