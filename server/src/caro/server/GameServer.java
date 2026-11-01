package caro.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class GameServer {
    private final String host;
    private final int port;
    private final int readTimeoutMs;
    private final ConcurrentHashMap<String, ClientHandler> names = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger roomSequence = new AtomicInteger(1000);

    GameServer(String host, int port, int readTimeoutMs) { this.host = host; this.port = port; this.readTimeoutMs = readTimeoutMs; }

    void start() throws IOException {
        try (ServerSocket listener = new ServerSocket()) {
            listener.bind(new InetSocketAddress(host, port));
            System.out.printf("Caro server listening on %s:%d%n", host, listener.getLocalPort());
            while (!listener.isClosed()) {
                Socket socket = listener.accept();
                socket.setKeepAlive(true);
                socket.setSoTimeout(readTimeoutMs);
                Thread.ofPlatform().name("client-" + socket.getRemoteSocketAddress()).start(new ClientHandler(this, socket));
            }
        }
    }

    boolean registerName(String name, ClientHandler client) { return names.putIfAbsent(name.toLowerCase(), client) == null; }
    void unregisterName(String name, ClientHandler client) { if (name != null) names.remove(name.toLowerCase(), client); }
    Room createRoom(ClientHandler owner) { Room room = new Room(String.valueOf(roomSequence.incrementAndGet())); rooms.put(room.id(), room); return room; }
    Room room(String id) { return rooms.get(id); }
    void removeIfEmpty(Room room) { if (room != null && room.empty()) rooms.remove(room.id(), room); }
    String roomList() { List<String> list = new ArrayList<>(); rooms.values().stream().sorted((a,b)->a.id().compareTo(b.id())).forEach(r -> list.add(r.summary())); return String.join(";", list); }
}
