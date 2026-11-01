package caro.server;

import caro.protocol.TextProtocol;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class ClientHandler implements Runnable {
    private static final int MAX_FRAME = 8192;
    private final GameServer server;
    private final Socket socket;
    private final AtomicBoolean closed = new AtomicBoolean();
    private BufferedWriter writer;
    private String name;
    private volatile Room room;

    ClientHandler(GameServer server, Socket socket) { this.server = server; this.socket = socket; }
    String name() { return name == null ? "anonymous" : name; }

    @Override public void run() {
        try (socket; BufferedInputStream input = new BufferedInputStream(socket.getInputStream())) {
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            send(TextProtocol.encode("server-ready", "CAP/1.0", "Gui set-name,<ten> de bat dau"));
            while (!closed.get()) {
                String frame = readFrame(input);
                if (frame == null) break;
                handle(frame);
            }
        } catch (SocketTimeoutException error) {
            sendQuietly(TextProtocol.encode("error", 408, "Ket noi het thoi gian cho"));
        } catch (FrameTooLargeException error) {
            sendQuietly(TextProtocol.encode("error", 413, "Frame vuot qua 8192 byte"));
        } catch (IOException error) {
            if (!closed.get()) System.out.println("Connection ended: " + error.getMessage());
        } finally { cleanup(true); }
    }

    private void handle(String frame) {
        List<String> fields;
        try { fields = TextProtocol.decode(frame); }
        catch (IllegalArgumentException error) { error(400, "Plain-text frame khong hop le: " + error.getMessage()); return; }
        if (fields.isEmpty() || fields.getFirst().isBlank()) { error(400, "Thieu ten lenh"); return; }
        String command = fields.getFirst().toLowerCase();
        try {
            switch (command) {
                case "hello" -> { exact(fields, 1); send(TextProtocol.encode("hello-ok", "CAP/1.0")); }
                case "set-name" -> { exact(fields, 2); setName(fields.get(1)); }
                case "ping" -> { exact(fields, 1); send("pong"); }
                case "view-room-list" -> { exact(fields, 1); requireNamed(); send(TextProtocol.encode("room-list", server.roomList())); }
                case "create-room" -> { range(fields, 1, 2); createRoom(fields.size() == 2 ? fields.get(1) : ""); }
                case "join-room" -> { range(fields, 2, 3); joinRoom(fields.get(1), fields.size() == 3 && "spectator".equalsIgnoreCase(fields.get(2))); }
                case "left-room" -> { exact(fields, 1); leaveRoom(false); }
                case "caro" -> { exact(fields, 3); move(integer(fields.get(1), "row"), integer(fields.get(2), "col")); }
                case "chat" -> { exact(fields, 2); chat(fields.get(1)); }
                case "quit" -> { exact(fields, 1); send("quit-ack"); cleanup(false); }
                default -> error(400, "Lenh khong duoc ho tro: " + command);
            }
        } catch (CommandException error) { error(error.code, error.getMessage()); }
        catch (RuntimeException error) { error(400, "Du lieu lenh khong hop le"); }
    }

    private void setName(String requested) throws CommandException {
        if (name != null) throw new CommandException(409, "Ten da duoc dat cho session nay");
        if (requested == null || !requested.matches("[A-Za-z0-9_]{3,20}"))
            throw new CommandException(400, "Ten gom 3..20 ky tu chu, so hoac _");
        if (!server.registerName(requested, this)) throw new CommandException(409, "Ten dang duoc su dung");
        name = requested;
        send(TextProtocol.encode("name-accepted", requested));
    }

    private void createRoom(String password) throws CommandException {
        requireNamed(); requireNoRoom();
        if (!password.isBlank()) throw new CommandException(400, "Phien ban nay chi ho tro phong public");
        Room created = server.createRoom(this);
        room = created;
        Room.Result result = created.join(this, false);
        send(TextProtocol.encode("your-created-room", created.id()));
        dispatch(result.dispatches());
    }

    private void joinRoom(String id, boolean spectator) throws CommandException {
        requireNamed(); requireNoRoom();
        Room target = server.room(id);
        if (target == null) throw new CommandException(404, "Khong tim thay phong");
        Room.Result result = target.join(this, spectator);
        ensure(result);
        room = target;
        send(TextProtocol.encode("join-room-success", id, target.role(this)));
        dispatch(result.dispatches());
    }

    private void move(int row, int col) throws CommandException {
        Room.Result result = requireRoom().move(this, row, col);
        ensure(result);
        send(TextProtocol.encode("move-accepted", row, col));
        dispatch(result.dispatches());
    }

    private void chat(String content) throws CommandException {
        Room.Result result = requireRoom().chat(this, content);
        ensure(result);
        send("chat-accepted");
        dispatch(result.dispatches());
    }

    private void leaveRoom(boolean disconnected) throws CommandException {
        Room current = requireRoom();
        Room.Result result = current.leave(this, disconnected);
        room = null;
        server.removeIfEmpty(current);
        if (!disconnected) send(TextProtocol.encode("left-room", current.id()));
        dispatch(result.dispatches());
    }

    private void cleanup(boolean disconnected) {
        if (!closed.compareAndSet(false, true)) return;
        Room current = room;
        room = null;
        if (current != null) {
            Room.Result result = current.leave(this, disconnected);
            dispatch(result.dispatches());
            server.removeIfEmpty(current);
        }
        server.unregisterName(name, this);
        try { socket.close(); } catch (IOException ignored) {}
    }

    synchronized void send(String frame) {
        if (writer == null || closed.get()) return;
        try { writer.write(frame); writer.newLine(); writer.flush(); }
        catch (IOException error) { cleanup(true); }
    }

    private void sendQuietly(String frame) {
        try { if (writer != null) { writer.write(frame); writer.newLine(); writer.flush(); } }
        catch (IOException ignored) {}
    }

    private void dispatch(List<Room.Dispatch> events) {
        for (Room.Dispatch event : events)
            for (ClientHandler recipient : event.recipients()) recipient.send(event.frame());
    }

    private void error(int code, String message) { send(TextProtocol.encode("error", code, message)); }
    private void ensure(Room.Result result) throws CommandException { if (!result.ok()) throw new CommandException(result.code(), result.message()); }
    private void requireNamed() throws CommandException { if (name == null) throw new CommandException(401, "Can set-name truoc"); }
    private void requireNoRoom() throws CommandException { if (room != null) throw new CommandException(409, "Hay roi phong hien tai truoc"); }
    private Room requireRoom() throws CommandException { requireNamed(); if (room == null) throw new CommandException(409, "Ban chua o trong phong"); return room; }
    private static int integer(String value, String field) throws CommandException { try { return Integer.parseInt(value); } catch (NumberFormatException error) { throw new CommandException(400, field + " phai la so nguyen"); } }
    private static void exact(List<String> fields, int count) throws CommandException { if (fields.size() != count) throw new CommandException(400, "So tham so khong hop le"); }
    private static void range(List<String> fields, int min, int max) throws CommandException { if (fields.size() < min || fields.size() > max) throw new CommandException(400, "So tham so khong hop le"); }

    private static String readFrame(BufferedInputStream input) throws IOException, FrameTooLargeException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') break;
            if (bytes.size() >= MAX_FRAME) throw new FrameTooLargeException();
            if (value != '\r') bytes.write(value);
        }
        if (value == -1 && bytes.size() == 0) return null;
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static final class CommandException extends Exception {
        final int code;
        CommandException(int code, String message) { super(message); this.code = code; }
    }
    private static final class FrameTooLargeException extends Exception {}
}
