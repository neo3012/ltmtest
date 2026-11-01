# Project Proposal — Milestone M1

> Keep it to **1–2 pages**. This document is the main evidence for rubric criterion **A1 — Problem & Idea**.
> AI may help you brainstorm or critique — log it in [`ai-log.md`](ai-log.md) — but the problem, the choices and
> the reasoning must be the team's own. Expect to be asked about this document in the oral defense.

---

## 1. Team & Topic

- **Team:** INT1433-04-0008 — Phạm Huy Tùng, Cao Trung Kiên, Đào Khánh Ninh
- **Project:** **Caro Arena — Trò chơi Caro nhiều người qua mạng**
- **Topic:** Trò chơi mạng nhiều phòng có chế độ khán giả

## 2. Problem

Cờ Caro vốn quen thuộc và dễ chơi khi hai người ngồi cùng một chỗ. Tuy nhiên, để hai người có thể chơi trên hai
máy tính khác nhau, hệ thống cần một server chung để quản lý bàn cờ, lượt đi và kết quả trận đấu. Nếu việc đồng bộ
không được xử lý đúng, hai người chơi có thể nhìn thấy hai trạng thái bàn cờ khác nhau, đánh trùng một ô hoặc nhận
kết quả không thống nhất khi kết nối gặp sự cố.

Từ vấn đề đó, nhóm muốn xây dựng một trò chơi Caro qua mạng. Dự án không
chỉ hướng tới việc tạo ra một trò chơi có thể sử dụng được, mà còn giúp nhóm trực tiếp thiết kế giao thức trao đổi
dữ liệu, xử lý nhiều kết nối cùng lúc và giải quyết các tình huống mất kết nối trong quá trình chơi.

## 3. Idea

Caro Arena hỗ trợ các trận Caro 1 đấu 1 theo thời gian thực trong nhiều phòng. Server có thẩm
quyền, quản lý tên người dùng, lượt chơi, bàn cờ và kết quả; client chỉ gửi thao tác và hiển thị trạng thái đã được
server xác nhận. Khán giả có thể vào phòng đang thi đấu và nhận cập nhật trực tiếp nhưng không được đánh cờ. Phiên
bản đầu lưu trạng thái hoạt động trong bộ nhớ và dành chức năng lưu trữ lâu dài cho phiên bản sau. Việc phục vụ
đồng thời người chơi và khán giả đòi hỏi kiểm tra vai trò, bảo đảm mỗi lần cập nhật phòng được hoàn thành trước khi xử lý yêu cầu tiếp theo và broadcast kết quả nhất quán.

## 4. Scope

| Must have for M3 | Nice to have | Out of scope for the first version |
|------------------|--------------|------------------------------------|
| Đặt tên hiển thị không trùng | Chơi lại trong cùng phòng | Cơ sở dữ liệu lâu dài và mật khẩu tài khoản |
| Liệt kê, tạo, tham gia và rời phòng | Giới hạn thời gian mỗi lượt | Đối thủ do máy tính điều khiển |
| Chơi 1 đấu 1 trên bàn cờ 15 × 15 | Kết nối lại trong khoảng chờ ngắn | Ghép trận và xếp hạng toàn hệ thống |
| Server kiểm tra lượt, ô cờ, thắng và hòa | Chat văn bản trong phòng | Hiệu ứng đồ họa phức tạp hoặc giao diện 3D |
| Nhiều phòng hoạt động đồng thời | Lịch sử các trận gần đây trong bộ nhớ | Chống gian lận ở mức sản phẩm thương mại |
| Khán giả nhận cập nhật trận đấu trực tiếp | — | — |
| Xử lý dữ liệu sai, timeout và mất kết nối đột ngột | — | — |

Luật chơi ban đầu được quy định rõ: tọa độ nằm trong khoảng từ `0` đến `14`, người chơi `X` đi trước và người chơi
thắng khi tạo được ít nhất năm quân liên tiếp theo hàng ngang, dọc hoặc chéo. Phiên bản đầu không áp dụng luật chặn
hai đầu. Nếu người chơi mất kết nối khi trận đang diễn ra, người đó bị xử thua; hỗ trợ kết nối lại là tính năng mở
rộng không bắt buộc.

## 5. Key Networking Challenge

Thách thức chính là duy trì một trạng thái trận đấu thống nhất khi các thao tác đánh cờ, vào phòng và mất kết nối
xảy ra đồng thời. Mỗi lệnh `caro` được xử lý trọn vẹn, không để yêu cầu khác xen vào giữa: trong khi giữ khóa phòng, server kiểm tra session, vai
trò, lượt, tọa độ và ô cờ, sau đó cập nhật bàn cờ và tạo snapshot kết quả. Server mở khóa trước khi broadcast để
một client mạng chậm không thể chặn cả phòng và các phòng khác vẫn hoạt động độc lập.

TCP không giữ ranh giới thông điệp, do đó hai đầu kết nối phải lưu dữ liệu vào buffer và ghép lại các frame plain text
CAP/1.0 được phân tách bằng ký tự xuống dòng. Frame sai định dạng hoặc lớn hơn 8 KB bị từ chối mà không làm server dừng.
Mọi đường kết thúc kết nối đều phải xóa client khỏi session và phòng rồi đóng socket.

## 6. Initial Design Sketch

- **Transport:** TCP vì các lệnh phải được truyền tin cậy và đúng thứ tự.
- **Framing and format:** plain text UTF-8 dạng `command,param...`, mỗi thông điệp kết thúc bằng `\n`, tối đa 8 KB.
- **Session model:** có trạng thái; sau `set-name`, danh tính và phòng hiện tại được gắn với kết nối và lưu trong
  bộ nhớ.
- **Concurrency model:** mỗi kết nối có một handler độc lập và mỗi phòng có một khóa riêng. Danh sách session và
  phòng dùng collection thread-safe hoặc cơ chế đồng bộ tương đương.
- **Language and build:** Java và Maven cho cả client lẫn server.
- **Deployment:** server chạy trong Docker, bind `0.0.0.0`; host và port lấy từ biến môi trường.

| Initial command | Direction | Purpose |
|-----------------|:---------:|---------|
| `set-name` | Client → Server | Đăng ký tên hiển thị không trùng cho kết nối |
| `create-room` | Client → Server | Tạo một phòng đang chờ |
| `join-room` | Client → Server | Tham gia với vai trò người chơi hoặc khán giả |
| `caro` | Client → Server | Yêu cầu đánh quân tại một tọa độ |
| `quit` | Client → Server | Rời hệ thống và đóng kết nối đúng cách |

## 7. Alternatives Considered

| Decision | Chosen option | Alternative | Reasoning and trade-off |
|----------|:-------------:|-------------|-------------------------|
| Transport | TCP | UDP | Truyền tin cậy và đúng thứ tự quan trọng hơn mức overhead thấp của UDP. |
| Framing | Plain text phân tách bằng xuống dòng | Tiền tố độ dài 4 byte | Dễ quan sát/debug; escape dấu phẩy và xuống dòng trong payload. |
| Concurrency | Handler theo client, khóa theo phòng | Thread pool hoặc I/O bất đồng bộ | Dễ triển khai và bảo vệ với quy mô dự kiến của đồ án. |
| Authority | Server có thẩm quyền | Client tự tính trạng thái | Ngăn bàn cờ xung đột và các nước đi không có quyền. |
| Storage | Trong bộ nhớ | Cơ sở dữ liệu | Giúp M2 tập trung vào networking nhưng vẫn có thể bổ sung lưu trữ sau này. |

## 8. Ownership Plan

| Member | Will own modules and documents |
|--------|--------------------------------|
| Phạm Huy Tùng | |
| Cao Trung Kiên | |
| Đào Khánh Ninh | |

> Split by **vertical slices or components** (e.g., protocol + parser, session manager, client), not "one person
> writes code, another writes docs". Everyone is examined individually on what they own.

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Các nước đi đồng thời làm sai trạng thái phòng | Kiểm tra và cập nhật dưới một khóa phòng; kiểm thử các nước đi đồng thời |
| Client mạng chậm chặn người khác | Sao chép danh sách người nhận khi giữ khóa, sau đó mở khóa rồi mới ghi socket |
| Mất kết nối để lại trạng thái rác | Tập trung cleanup có tính idempotent trong khối `finally` của handler |
| Frame lỗi làm cạn tài nguyên | Giới hạn 8 KB, kiểm tra trường dữ liệu và cô lập kết nối vi phạm |
| Khán giả gửi lệnh dành cho người chơi | Kiểm tra vai trò trên mọi lệnh thay đổi trạng thái và trả lỗi quyền truy cập |
| Phạm vi vượt quá tiến độ | Hoàn thiện luồng must-have trước rematch, reconnect, chat và lịch sử |

## 10. Instructor Feedback

*(Filled in after M1 review.)*
