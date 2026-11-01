# Client

Client Java 21 gồm hai entry point dùng chung `GameConnection` và CAP/1.0:

- `caro.client.Main`: JavaFX GUI có kết nối, danh sách phòng, bàn 15×15, vai trò người chơi/khán giả và chat.
- `caro.client.CliMain`: CLI headless cho Docker và debug protocol.

Chạy GUI native (cần JDK 21+ và Maven):

```bash
cd client
mvn clean javafx:run
```

Mặc định giao diện kết nối `localhost:5000`; có thể nhập host/port khác trên màn hình. Client chỉ gửi hành động, còn server quyết định lượt và kết quả.
