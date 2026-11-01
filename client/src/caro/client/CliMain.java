package caro.client;

import java.util.List;
import java.util.Scanner;

/** Headless client retained for Docker demos and protocol debugging. */
public final class CliMain {
    private CliMain() {}

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("CLIENT_TARGET_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("CLIENT_TARGET_PORT", "5000"));
        Scanner console = new Scanner(System.in);
        System.out.print("Ten nguoi choi: ");
        String name = console.nextLine().trim();
        GameConnection connection = new GameConnection(
                fields -> System.out.println("< " + TextProtocol.encode(fields.toArray())),
                System.out::println);
        connection.connect(host, port, name);
        System.out.println("Nhap frame CAP/1.0, vi du: view-room-list | create-room | join-room,1001 | caro,7,7 | quit");
        while (console.hasNextLine() && connection.connected()) {
            List<String> fields = TextProtocol.decode(console.nextLine());
            connection.send(fields.toArray());
            if (!fields.isEmpty() && "quit".equalsIgnoreCase(fields.getFirst())) break;
        }
        connection.close();
    }
}
