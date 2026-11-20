package tsapper.splitscreen_ultimate.client.session;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bounded background IO. Minecraft callbacks are scheduled by the receiver, never by this class. */
public final class IpcClient implements AutoCloseable {
    public static final Gson GSON = new Gson();
    private final BlockingQueue<String> outgoing = new ArrayBlockingQueue<>(512);
    private volatile Socket socket;
    private volatile boolean closed;
    private final Consumer<String> failure;
    public IpcClient(LaunchConfig config, long hwnd, Consumer<JsonObject> receiver, Consumer<String> failure) {
        this.failure = failure;
        Thread.ofPlatform().daemon().name("splitscreen-ipc").start(() -> {
            try {
                Socket connection = new Socket(); socket = connection;
                connection.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), config.port()), 10_000);
                connection.setTcpNoDelay(true);
                var writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
                JsonObject hello = message("hello");
                hello.addProperty("version", 1); hello.addProperty("session", config.session());
                hello.addProperty("instance", config.instance()); hello.addProperty("token", config.token());
                hello.addProperty("slot", config.slot()); hello.addProperty("hwnd", Long.toUnsignedString(hwnd));
                writer.write(hello.toString()); writer.newLine(); writer.flush();
                Thread.ofPlatform().daemon().name("splitscreen-ipc-writer").start(() -> {
                    try {
                        while (!closed) { String line = outgoing.poll(1, TimeUnit.SECONDS); if (line != null) {
                            writer.write(line); writer.newLine(); writer.flush();
                        } }
                    } catch (Exception e) { fail(e); }
                });
                var reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                while (!closed) {
                    String line = readLine(reader);
                    if (line == null) throw new EOFException("Launcher disconnected");
                    receiver.accept(JsonParser.parseString(line).getAsJsonObject());
                }
            } catch (Exception e) { fail(e); }
        });
    }
    private static String readLine(Reader reader) throws IOException {
        var line = new StringBuilder(); int c;
        while ((c = reader.read()) != -1 && c != '\n') {
            if (line.length() >= 65536) throw new IOException("IPC frame too large");
            if (c != '\r') line.append((char)c);
        }
        return c == -1 && line.isEmpty() ? null : line.toString();
    }
    public static JsonObject message(String type) { var m = new JsonObject(); m.addProperty("type", type); return m; }
    public void send(JsonObject message) {
        if (!closed && !outgoing.offer(message.toString())) fail(new IOException("IPC output overflow"));
    }
    private synchronized void fail(Exception e) {
        if (closed) return;
        close(); failure.accept(e.getMessage() == null ? e.toString() : e.getMessage());
    }
    public synchronized void close() {
        closed = true;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
    }
}
