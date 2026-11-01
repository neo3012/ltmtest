# Hướng dẫn chạy Caro Arena

Tài liệu này hướng dẫn chạy server, JavaFX GUI, client CLI trong Docker và các bài kiểm thử trên Windows.

## 1. Yêu cầu

- JDK 21 trở lên: `java -version` và `javac -version`.
- Apache Maven 3.9 trở lên: `mvn -version`.
- Docker Desktop có Docker Compose v2: `docker compose version`.
- Python 3 nếu muốn chạy test: `python --version`.

Mở PowerShell hoặc Command Prompt tại thư mục gốc:

```text
C:\Users\LENOVO\OneDrive\Desktop\LTM\int1433-04-20261-grp-int1433-04-0008
```

Port mặc định của server là `5000`.

## 2. Cách khuyến nghị: server Docker, JavaFX chạy native

### Bước 1 — Tạo file cấu hình

PowerShell:

```powershell
Copy-Item .env.example .env
```

Command Prompt:

```cmd
copy .env.example .env
```

Nếu `.env` đã tồn tại thì không cần sao chép lại. Hai biến port phải đồng nhất:

```env
SERVER_PORT=5000
CLIENT_TARGET_PORT=5000
```

### Bước 2 — Khởi động server

```powershell
docker compose up --build -d server
docker compose ps
docker compose logs server
```

Server chạy thành công khi log có:

```text
Caro server listening on 0.0.0.0:5000
```

### Bước 3 — Mở JavaFX client thứ nhất

Có thể đứng tại thư mục gốc và chỉ rõ `pom.xml`:

```powershell
mvn -f client\pom.xml clean javafx:run
```

Trong giao diện nhập:

```text
Host: localhost
Port: 5000
Tên: alice
```

Nhấn **Kết nối**, sau đó nhấn **Tạo phòng** và ghi lại ID phòng.

### Bước 4 — Mở JavaFX client thứ hai

Mở PowerShell mới tại thư mục gốc:

```powershell
mvn -f client\pom.xml javafx:run
```

Không dùng `clean` khi client thứ nhất đang chạy. Đặt tên khác, ví dụ `bob`, nhập ID phòng rồi nhấn **Vào chơi**.

Client X đi trước. Khi đúng lượt, nhấn trực tiếp vào một ô trên bàn cờ.

### Bước 5 — Mở khán giả nếu cần

Mở thêm một client:

```powershell
mvn -f client\pom.xml javafx:run
```

Đặt tên khác, nhập ID phòng và nhấn **Xem**. Khán giả xem bàn cờ và chat được nhưng không được đánh.

## 3. Chạy client CLI trong Docker

CLI dùng để debug protocol và chứng minh một command chạy end-to-end trong Docker:

```powershell
docker compose run --rm client
```

Khi chương trình hỏi tên, nhập ví dụ:

```text
alice_cli
```

Sau đó gửi một trong các command CAP/1.0:

```text
ping
view-room-list
create-room
join-room,1001,player
caro,7,7
chat,Xin chao
left-room
quit
```

Ví dụ `ping` thành công sẽ nhận:

```text
< pong
```

JavaFX GUI không chạy trực tiếp trong container hiện tại vì container không có display server. GUI chạy native, còn server và CLI có thể chạy trong Docker.

## 4. Chạy toàn bộ không dùng Docker

### Server native

PowerShell tại thư mục gốc:

```powershell
New-Item -ItemType Directory -Force server\out
$serverSources = Get-ChildItem -Recurse server\src -Filter *.java | ForEach-Object FullName
javac --release 21 -encoding UTF-8 -d server\out $serverSources
$env:SERVER_HOST = "127.0.0.1"
$env:SERVER_PORT = "5000"
java -cp server\out caro.server.Main
```

Giữ cửa sổ server đang chạy và mở client ở terminal khác:

```powershell
mvn -f client\pom.xml clean javafx:run
```

## 5. Chạy kiểm thử

Khi server đang lắng nghe tại `localhost:5000`:

```powershell
python tests\integration_test.py
python tests\load_test.py
```

Kết quả mong đợi:

```text
PASS: CAP/1.0 plain text, 3 clients, 9 moves, win, escaped comma chat, malformed frame recovery
PASS: 50/50 concurrent CAP/1.0 clients completed set-name and ping
```

## 6. Dừng và build lại Docker

Dừng container:

```powershell
docker compose down
```

Sau khi sửa source code, build và chạy lại:

```powershell
docker compose down
docker compose up --build -d server
```

Khi nghi ngờ Docker đang dùng image cũ:

```powershell
docker compose build --no-cache server client
docker compose up -d server
```

## 7. Lỗi thường gặp

### `mvn` không được nhận diện

Maven chưa được thêm vào `PATH`. Kiểm tra các bản Maven đã có:

```powershell
Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter mvn.cmd
```

Chạy bằng đường dẫn tìm được:

```powershell
& "DUONG_DAN_DEN_MVN.CMD" -f client\pom.xml javafx:run
```

### `No plugin found for prefix 'javafx'`

Bạn đang chạy Maven ở thư mục gốc nhưng chưa chỉ rõ `pom.xml`. Dùng:

```powershell
mvn -f client\pom.xml javafx:run
```

Hoặc:

```powershell
cd client
mvn javafx:run
```

### Port 5000 đang được sử dụng

Kiểm tra container và tiến trình Docker cũ:

```powershell
docker compose ps
docker compose down
```

Sau đó khởi động lại server. Không chạy đồng thời server native và server Docker trên cùng port `5000`.

### JavaFX không kết nối được server

Kiểm tra:

1. Server đã chạy và log có port `5000`.
2. GUI dùng host `localhost`, không dùng `server`.
3. `.env` dùng `SERVER_PORT=5000`.
4. Không có server cũ đang chiếm port.

## 8. Tóm tắt lệnh demo

```powershell
# Terminal 1 — server Docker
docker compose up --build -d server

# Terminal 2 — Alice
mvn -f client\pom.xml clean javafx:run

# Terminal 3 — Bob
mvn -f client\pom.xml javafx:run

# Kết thúc demo
docker compose down
```
