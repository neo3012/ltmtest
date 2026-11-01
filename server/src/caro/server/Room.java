package caro.server;

import caro.protocol.TextProtocol;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Room {
    record Dispatch(List<ClientHandler> recipients, String frame) {}
    record Result(boolean ok, int code, String message, List<Dispatch> dispatches) {
        static Result error(int code, String message) { return new Result(false, code, message, List.of()); }
        static Result ok(List<Dispatch> dispatches) { return new Result(true, 200, "OK", dispatches); }
    }

    private final String id;
    private final char[][] board = new char[15][15];
    private final Set<ClientHandler> spectators = new LinkedHashSet<>();
    private ClientHandler x;
    private ClientHandler o;
    private char turn = 'X';
    private String status = "WAITING";

    Room(String id) { this.id = id; }
    String id() { return id; }

    synchronized Result join(ClientHandler client, boolean spectator) {
        if (contains(client)) return Result.error(409, "Ban da o trong phong");
        if (spectator) spectators.add(client);
        else if (x == null) x = client;
        else if (o == null) o = client;
        else return Result.error(409, "Phong da du hai nguoi choi");
        if (x != null && o != null && status.equals("WAITING")) status = "PLAYING";
        return Result.ok(List.of(stateDispatch()));
    }

    synchronized Result move(ClientHandler client, int row, int col) {
        if (!status.equals("PLAYING")) return Result.error(409, "Tran dau chua san sang hoac da ket thuc");
        char mark = client == x ? 'X' : client == o ? 'O' : '?';
        if (mark == '?') return Result.error(403, "Khan gia khong duoc danh co");
        if (mark != turn) return Result.error(409, "Chua den luot cua ban");
        if (row < 0 || row >= 15 || col < 0 || col >= 15) return Result.error(400, "Toa do phai nam trong 0..14");
        if (board[row][col] != 0) return Result.error(409, "O co da duoc danh");
        board[row][col] = mark;
        String outcome = "CONTINUE";
        if (wins(row, col, mark)) { status = "FINISHED"; outcome = "WIN"; }
        else if (full()) { status = "FINISHED"; outcome = "DRAW"; }
        else turn = turn == 'X' ? 'O' : 'X';
        String move = TextProtocol.encode("caro", row, col, mark, outcome,
                status.equals("PLAYING") ? String.valueOf(turn) : "-");
        List<Dispatch> events = new ArrayList<>();
        events.add(new Dispatch(recipients(), move));
        if (!outcome.equals("CONTINUE")) events.add(new Dispatch(recipients(), TextProtocol.encode(
                "game-over", outcome, outcome.equals("WIN") ? client.name() : "")));
        return Result.ok(events);
    }

    synchronized Result chat(ClientHandler sender, String content) {
        if (!contains(sender)) return Result.error(403, "Ban khong o trong phong");
        if (content == null || content.isBlank() || content.length() > 300) return Result.error(400, "Tin nhan phai co 1..300 ky tu");
        return Result.ok(List.of(new Dispatch(recipients(),
                TextProtocol.encode("chat", sender.name(), content))));
    }

    synchronized Result leave(ClientHandler client, boolean disconnected) {
        boolean wasPlayer = client == x || client == o;
        if (!wasPlayer && !spectators.remove(client)) return Result.error(409, "Ban khong o trong phong");
        if (client == x) x = null;
        if (client == o) o = null;
        String winner = "";
        if (wasPlayer && status.equals("PLAYING")) {
            status = "FINISHED";
            ClientHandler other = x != null ? x : o;
            winner = other == null ? "" : other.name();
        }
        List<ClientHandler> targets = recipients();
        List<Dispatch> events = new ArrayList<>();
        events.add(new Dispatch(targets, TextProtocol.encode("player-left", client.name(),
                disconnected ? "DISCONNECTED" : "LEFT", winner)));
        events.add(new Dispatch(targets, stateFrame()));
        return Result.ok(events);
    }

    synchronized boolean empty() { return x == null && o == null && spectators.isEmpty(); }
    synchronized boolean contains(ClientHandler client) { return client == x || client == o || spectators.contains(client); }
    synchronized String summary() { return id + ":" + status + ":" + (x == null ? "-" : x.name()) + ":" + (o == null ? "-" : o.name()) + ":" + spectators.size(); }
    synchronized String role(ClientHandler client) { return client == x ? "X" : client == o ? "O" : spectators.contains(client) ? "SPECTATOR" : "NONE"; }

    private Dispatch stateDispatch() { return new Dispatch(recipients(), stateFrame()); }
    private String stateFrame() {
        return TextProtocol.encode("room-state", id, status,
                x == null ? "" : x.name(), o == null ? "" : o.name(),
                turn, spectators.size(), boardString());
    }
    private List<ClientHandler> recipients() { List<ClientHandler> all = new ArrayList<>(spectators); if (x != null) all.add(x); if (o != null) all.add(o); return List.copyOf(all); }
    private String boardString() { StringBuilder out = new StringBuilder(225); for (char[] row : board) for (char c : row) out.append(c == 0 ? '.' : c); return out.toString(); }
    private boolean full() { for (char[] row : board) for (char c : row) if (c == 0) return false; return true; }
    private boolean wins(int row, int col, char mark) { int[][] d = {{1,0},{0,1},{1,1},{1,-1}}; for (int[] v : d) if (1 + count(row,col,v[0],v[1],mark) + count(row,col,-v[0],-v[1],mark) >= 5) return true; return false; }
    private int count(int r, int c, int dr, int dc, char mark) { int n=0; for (r+=dr,c+=dc; r>=0&&r<15&&c>=0&&c<15&&board[r][c]==mark; r+=dr,c+=dc) n++; return n; }
}
