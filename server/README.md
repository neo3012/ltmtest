# Server

Java 21 TCP server, entry point `caro.server.Main`. Cấu hình: `SERVER_HOST` (mặc định `0.0.0.0`), `SERVER_PORT` (5000), `CLIENT_READ_TIMEOUT_MS` (120000). Server dùng plain-text CAP/1.0 và giữ room/session trong bộ nhớ; restart sẽ xóa trạng thái.

Xem `../docs/architecture.md` và `../docs/protocol-design.md` trước khi sửa code.
