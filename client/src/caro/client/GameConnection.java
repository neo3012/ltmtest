package caro.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

final class GameConnection implements AutoCloseable {
    private final Consumer<List<String>> messageHandler;
    private final Consumer<String> disconnectHandler;
    private Socket socket;
    private BufferedWriter writer;

    GameConnection(Consumer<List<String>> messageHandler, Consumer<String> disconnectHandler) {
        this.messageHandler = messageHandler;
        this.disconnectHandler = disconnectHandler;
    }

    void connect(String host, int port, String name) throws IOException {
        socket = new Socket(host, port);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        Thread.ofPlatform().daemon().name("caro-server-reader").start(() -> readLoop(reader));
        send("set-name", name);
    }

    synchronized void send(Object... fields) throws IOException {
        if (writer == null) throw new IOException("Chua ket noi server");
        writer.write(TextProtocol.encode(fields));
        writer.newLine();
        writer.flush();
    }

    boolean connected() { return socket != null && socket.isConnected() && !socket.isClosed(); }

    private void readLoop(BufferedReader reader) {
        String reason = "Server da dong ket noi";
        try {
            String frame;
            while ((frame = reader.readLine()) != null) messageHandler.accept(TextProtocol.decode(frame));
        } catch (Exception error) {
            reason = "Mat ket noi: " + error.getMessage();
        } finally {
            try { close(); } catch (Exception ignored) {}
            disconnectHandler.accept(reason);
        }
    }

    @Override public synchronized void close() throws IOException {
        if (socket != null) socket.close();
        socket = null;
        writer = null;
    }
}
