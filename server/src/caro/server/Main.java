package caro.server;

public final class Main {
    private Main() {}
    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("SERVER_HOST", "0.0.0.0");
        int port = integerEnv("SERVER_PORT", 5000);
        int timeout = integerEnv("CLIENT_READ_TIMEOUT_MS", 120_000);
        new GameServer(host, port, timeout).start();
    }
    private static int integerEnv(String name, int fallback) {
        try { return Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(fallback))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + " must be an integer", e); }
    }
}
