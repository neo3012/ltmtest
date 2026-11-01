package caro.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class Main extends Application {
    private final Button[][] cells = new Button[15][15];
    private final char[] board = new char[225];
    private final ListView<String> rooms = new ListView<>();
    private final TextArea chatLog = new TextArea();
    private final TextField roomId = new TextField();
    private final TextField chatInput = new TextField();
    private final Label connectionStatus = new Label("Chua ket noi");
    private final Label gameStatus = new Label("Chua vao phong");
    private final Button connectButton = new Button("Ket noi");
    private GameConnection connection;
    private boolean named;
    private String role = "NONE";
    private String turn = "X";
    private String gameState = "WAITING";

    public Main() { Arrays.fill(board, '.'); }

    @Override public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setTop(connectionPane());
        root.setLeft(roomPane());
        root.setCenter(boardPane());
        root.setRight(chatPane());
        root.setBottom(gameStatus);
        BorderPane.setMargin(gameStatus, new Insets(10, 0, 0, 0));

        Scene scene = new Scene(root, 1120, 720);
        stage.setTitle("Caro Arena - CAP/1.0");
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setOnCloseRequest(event -> disconnect());
        stage.show();
        renderBoard();
    }

    private HBox connectionPane() {
        TextField host = new TextField("localhost");
        host.setPrefWidth(130);
        TextField port = new TextField("5000");
        port.setPrefWidth(70);
        TextField name = new TextField();
        name.setPromptText("Ten 3-20 ky tu");
        connectButton.setOnAction(event -> {
            try {
                int targetPort = Integer.parseInt(port.getText().trim());
                String targetHost = host.getText().trim();
                String playerName = name.getText().trim();
                if (!playerName.matches("[A-Za-z0-9_]{3,20}")) {
                    showError("Ten gom 3-20 ky tu chu, so hoac _");
                    return;
                }
                connectButton.setDisable(true);
                connectionStatus.setText("Dang ket noi...");
                Thread.ofPlatform().daemon().start(() -> {
                    try {
                        GameConnection candidate = new GameConnection(this::receiveAsync, this::disconnectedAsync);
                        connection = candidate;
                        candidate.connect(targetHost, targetPort, playerName);
                    } catch (Exception error) {
                        Platform.runLater(() -> {
                            connection = null;
                            connectButton.setDisable(false);
                            connectionStatus.setText("Ket noi that bai: " + error.getMessage());
                        });
                    }
                });
            } catch (NumberFormatException error) {
                showError("Port phai la so nguyen");
            }
        });
        HBox pane = new HBox(8, new Label("Host"), host, new Label("Port"), port,
                new Label("Ten"), name, connectButton, connectionStatus);
        pane.setAlignment(Pos.CENTER_LEFT);
        pane.setPadding(new Insets(0, 0, 12, 0));
        return pane;
    }

    private VBox roomPane() {
        rooms.setPrefWidth(230);
        rooms.setPrefHeight(420);
        rooms.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> {
            if (value != null && !value.isBlank()) roomId.setText(value.split(":", 2)[0]);
        });
        roomId.setPromptText("ID phong");
        Button refresh = new Button("Lam moi");
        refresh.setOnAction(event -> send("view-room-list"));
        Button create = new Button("Tao phong");
        create.setOnAction(event -> send("create-room"));
        Button join = new Button("Vao choi");
        join.setOnAction(event -> send("join-room", roomId.getText().trim(), "player"));
        Button watch = new Button("Xem");
        watch.setOnAction(event -> send("join-room", roomId.getText().trim(), "spectator"));
        Button leave = new Button("Roi phong");
        leave.setOnAction(event -> send("left-room"));
        HBox actions = new HBox(6, join, watch);
        VBox pane = new VBox(8, new Label("Danh sach phong"), rooms, refresh, create,
                new Label("Phong can vao"), roomId, actions, leave);
        pane.setPadding(new Insets(0, 12, 0, 0));
        return pane;
    }

    private ScrollPane boardPane() {
        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);
        grid.setAlignment(Pos.CENTER);
        for (int row = 0; row < 15; row++) {
            for (int col = 0; col < 15; col++) {
                Button cell = new Button();
                cell.setMinSize(32, 32);
                cell.setMaxSize(32, 32);
                final int selectedRow = row;
                final int selectedCol = col;
                cell.setOnAction(event -> send("caro", selectedRow, selectedCol));
                cells[row][col] = cell;
                grid.add(cell, col, row);
            }
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setPannable(true);
        return scroll;
    }

    private VBox chatPane() {
        chatLog.setEditable(false);
        chatLog.setWrapText(true);
        chatLog.setPrefWidth(260);
        chatInput.setPromptText("Nhap tin nhan...");
        Button sendChat = new Button("Gui");
        sendChat.setOnAction(event -> sendChat());
        chatInput.setOnAction(event -> sendChat());
        HBox row = new HBox(6, chatInput, sendChat);
        HBox.setHgrow(chatInput, Priority.ALWAYS);
        VBox pane = new VBox(8, new Label("Chat trong phong"), chatLog, row);
        VBox.setVgrow(chatLog, Priority.ALWAYS);
        pane.setPadding(new Insets(0, 0, 0, 12));
        return pane;
    }

    private void sendChat() {
        String content = chatInput.getText().trim();
        if (!content.isEmpty()) {
            send("chat", content);
            chatInput.clear();
        }
    }

    private void receiveAsync(List<String> fields) { Platform.runLater(() -> receive(fields)); }
    private void disconnectedAsync(String reason) {
        Platform.runLater(() -> {
            connection = null;
            named = false;
            connectButton.setDisable(false);
            connectionStatus.setText(reason);
        });
    }

    private void receive(List<String> fields) {
        if (fields.isEmpty()) return;
        try {
            switch (fields.getFirst()) {
                case "server-ready" -> connectionStatus.setText("Da ket noi " + field(fields, 1));
                case "name-accepted" -> { named = true; connectionStatus.setText("Xin chao " + field(fields, 1)); send("view-room-list"); }
                case "room-list" -> showRooms(field(fields, 1));
                case "your-created-room" -> { role = "X"; roomId.setText(field(fields, 1)); gameStatus.setText("Da tao phong " + field(fields, 1)); }
                case "join-room-success" -> { roomId.setText(field(fields, 1)); role = field(fields, 2); gameStatus.setText("Da vao phong voi vai tro " + role); }
                case "room-state" -> applyRoomState(fields);
                case "caro" -> applyMove(fields);
                case "chat" -> chatLog.appendText(field(fields, 1) + ": " + field(fields, 2) + System.lineSeparator());
                case "game-over" -> { gameState = "FINISHED"; gameStatus.setText("Ket thuc: " + field(fields, 1) + ", nguoi thang: " + field(fields, 2)); renderBoard(); }
                case "player-left" -> gameStatus.setText(field(fields, 1) + " da roi phong (" + field(fields, 2) + ")");
                case "left-room" -> resetRoom();
                case "error" -> {
                    showError("Loi " + field(fields, 1) + ": " + field(fields, 2));
                    if (!named) disconnect();
                }
                case "pong", "hello-ok", "move-accepted", "chat-accepted", "quit-ack" -> { }
                default -> chatLog.appendText("Server: " + TextProtocol.encode(fields.toArray()) + System.lineSeparator());
            }
        } catch (RuntimeException error) {
            showError("Message server khong hop le: " + TextProtocol.encode(fields.toArray()));
        }
    }

    private void applyRoomState(List<String> fields) {
        roomId.setText(field(fields, 1));
        gameState = field(fields, 2);
        turn = field(fields, 5);
        String snapshot = field(fields, 7);
        if (snapshot.length() == board.length) snapshot.getChars(0, snapshot.length(), board, 0);
        gameStatus.setText("Phong " + field(fields, 1) + " | X: " + field(fields, 3) +
                " | O: " + field(fields, 4) + " | Luot: " + turn + " | " + gameState);
        renderBoard();
    }

    private void applyMove(List<String> fields) {
        int row = Integer.parseInt(field(fields, 1));
        int col = Integer.parseInt(field(fields, 2));
        board[row * 15 + col] = field(fields, 3).charAt(0);
        turn = field(fields, 5);
        renderBoard();
    }

    private void renderBoard() {
        boolean myTurn = "PLAYING".equals(gameState) && role.equals(turn);
        for (int row = 0; row < 15; row++) {
            for (int col = 0; col < 15; col++) {
                char mark = board[row * 15 + col];
                Button cell = cells[row][col];
                cell.setText(mark == '.' ? "" : String.valueOf(mark));
                cell.setStyle(mark == 'X' ? "-fx-text-fill:#1565c0;-fx-font-weight:bold" :
                        mark == 'O' ? "-fx-text-fill:#c62828;-fx-font-weight:bold" : "");
                cell.setDisable(!myTurn || mark != '.');
            }
        }
    }

    private void showRooms(String summary) {
        rooms.getItems().clear();
        if (!summary.isBlank()) rooms.getItems().addAll(summary.split(";"));
    }

    private void resetRoom() {
        role = "NONE";
        turn = "X";
        gameState = "WAITING";
        roomId.clear();
        Arrays.fill(board, '.');
        gameStatus.setText("Da roi phong");
        renderBoard();
        send("view-room-list");
    }

    private void send(Object... fields) {
        try {
            if (connection == null || !connection.connected()) throw new IOException("Chua ket noi server");
            connection.send(fields);
        } catch (IOException error) {
            showError(error.getMessage());
        }
    }

    private void disconnect() {
        try {
            if (connection != null && connection.connected()) connection.send("quit");
            if (connection != null) connection.close();
        } catch (IOException ignored) {}
    }

    private static String field(List<String> fields, int index) {
        if (index >= fields.size()) throw new IllegalArgumentException("Thieu field " + index);
        return fields.get(index);
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Caro Arena");
        alert.setContentText(message);
        alert.show();
    }

    public static void main(String[] args) { launch(args); }
}
