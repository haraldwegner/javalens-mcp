package org.javalens.mcp.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Stdio transport: line-delimited JSON-RPC messages over an arbitrary
 * {@link InputStream} / {@link OutputStream} pair. The default real-world
 * wiring is {@code (System.in, System.out)}; tests inject byte-array streams.
 *
 * <p>Sprint 14a: extracted from the inline I/O loop in
 * {@code JavaLensApplication}. Behaviour is preserved exactly — UTF-8
 * encoding, blank-line skipping, auto-flush on write.
 */
public class StdioTransport implements Transport {

    private final BufferedReader reader;
    private final PrintWriter writer;

    public StdioTransport(InputStream in, OutputStream out) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new PrintWriter(out, true, StandardCharsets.UTF_8);
    }

    @Override
    public String readMessage() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    @Override
    public void writeMessage(String message) {
        writer.println(message);
        writer.flush();
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Best-effort close; the writer still flushes below.
        }
        writer.close();
    }
}
