# Testing Guide & Test Evidence

## 1. End-to-end CAP/1.0

Khởi động server ở port 5000 rồi chạy:

```bash
python tests/integration_test.py
```

Test mở ba socket thật: Alice (X), Bob (O), Viewer (spectator); tạo/tham gia phòng, đánh 9 nước, kiểm tra server phát hiện X thắng, chat có dấu phẩy được escape đúng, frame sai trả 400 và kết nối vẫn nhận `pong`.

Kết quả thực tế ngày 2026-11-01:

```text
PASS: CAP/1.0 plain text, 3 clients, 9 moves, win, escaped comma chat, malformed frame recovery
```

## 2. Concurrency

```bash
python tests/load_test.py
```

Kết quả thực tế:

```text
PASS: 50/50 concurrent CAP/1.0 clients completed set-name and ping
```

## 3. Docker

```bash
cp .env.example .env
docker compose up --build -d
docker compose logs server
python tests/integration_test.py
docker compose down
```

Kỳ vọng log có `Caro server listening on 0.0.0.0:5000`.

## 4. JavaFX GUI thủ công

| Tình huống | Thao tác | Kết quả mong đợi |
|---|---|---|
| Kết nối | Nhập host/port/tên hợp lệ | Hiện `Xin chao <name>`, tải danh sách phòng |
| Chơi 1v1 | Tạo phòng, client khác vào chơi | X/O và trạng thái `PLAYING` hiển thị đúng |
| Nước đi | Bấm ô khi đúng lượt | Cả hai GUI và khán giả thấy X/O tại cùng ô |
| Sai lượt | Cố gửi `caro` bằng CLI | `error,409,...`, board không đổi |
| Khán giả | Vào bằng nút **Xem** | Bàn cờ chỉ đọc, vẫn chat được |
| Chat dấu phẩy | Gửi `Xin chào, Bob` | Nội dung hiển thị nguyên vẹn ở mọi client |
| Mất kết nối | Đóng GUI đang chơi | Client còn lại nhận `player-left`; server tiếp tục chạy |

## 5. Bằng chứng

| # | Test | Kết quả | Pass? |
|:-:|---|---|:---:|
| 1 | Server compile Java 21 | Không có lỗi compiler | ✅ |
| 2 | CAP/1.0 game 3 client | Output PASS ở trên | ✅ |
| 3 | Plain-text malformed/escape | 400 rồi pong cùng session | ✅ |
| 4 | 50 client đồng thời | 50/50 hoàn thành | ✅ |
| 5 | Docker build CAP/1.0 mới | Cần chạy lại trên máy có Docker | ⬜ |
| 6 | JavaFX visual QA | Cần nhóm mở hai GUI và chụp ảnh thật | ⬜ |
