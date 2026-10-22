# Báo cáo mô tả công việc - Cơ sở dữ liệu (Issue #19)

- **Nhánh thực hiện**: `feature/2380600292-issue-19-database-full`
- **Người thực hiện**: Đỗ Tiến Dũng - MSSV: 2380600292, Nguyễn Tấn Kiệt - MSSV: 2380601152
- **Nội dung task**: Thiết kế, hoàn thiện và chuẩn hóa toàn bộ cơ sở dữ liệu (Database Schema & JSON Persistence) cho ứng dụng DAMMTP2P Chat & File Transfer

---

## 1. Mô tả thay đổi

### 1.1. Đã làm gì?
1. **Thiết kế và hoàn thiện lược đồ CSDL quan hệ chuẩn ([DATABASE/database.sql](file:///c:/Users/Admin/Downloads/23DTHB4-N5-LTMMT%20(1)/DATABASE/database.sql))**:
   - Tích hợp và hợp nhất toàn bộ các bảng dữ liệu từ các issue thành phần (#7, #8, #9, #10) thành một kịch bản CSDL đầy đủ.
   - Xây dựng 9 bảng dữ liệu:
     - `users`: Quản lý thông tin tài khoản, tên hiển thị, salt, hash mật khẩu, IP/Port kết nối gần nhất và trạng thái hoạt động.
     - `chat_rooms`: Quản lý các kênh chat nhóm và phòng thoại, khóa mã hóa phòng AES, người tạo phòng và trạng thái voice.
     - `room_members`: Quản lý thành viên từng phòng, phân quyền ADMIN/MEMBER, trạng thái mute/deafen.
     - `chat_messages`: Lưu trữ tin nhắn 1-1 và phòng chat, hỗ trợ mã hóa đầu cuối, vector khởi tạo IV, timestamp và trạng thái chuyển phát.
     - `file_transfers`: Quản lý lịch sử và trạng thái phiên truyền tệp P2P và Relay, lưu trữ offset bytes 64-bit hỗ trợ truyền tệp đứt đoạn, mã băm SHA-256 đối soát toàn vẹn, khóa mã hóa tệp AES.
     - `voice_sessions`: Ghi nhận các phiên đàm thoại VoIP qua giao thức UDP, theo dõi tổng số gói tin và lưu lượng byte.
     - `server_logs`: Bảng nhật ký kiểm toán hệ thống, phân loại sự kiện AUTH, TRANSFER, ROOM, VOICE, SYSTEM.
     - `app_settings`: Cấu hình tham số hệ thống gồm các cổng mạng TCP/UDP, giới hạn kích thước tệp và giao diện mặc định.
     - `friends`: Quản lý danh sách quan hệ bạn bè và trạng thái kết bạn.
   - Thiết lập các chỉ mục INDEX tối ưu hóa tốc độ tìm kiếm theo người gửi, phòng, mốc thời gian, loại sự kiện và trạng thái truyền file.
   - Nạp dữ liệu khởi tạo mẫu phục vụ việc chạy thử nghiệm và kiểm thử tích hợp.

2. **Chuẩn hóa bộ dữ liệu lưu trữ phi quan hệ (JSON Persistence Storage)**:
   - [`accounts.json`](file:///c:/Users/Admin/Downloads/23DTHB4-N5-LTMMT%20(1)/DATABASE/accounts.json): Dữ liệu xác thực người dùng với mật khẩu băm kèm salt độc lập cho từng user.
   - [`friends.json`](file:///c:/Users/Admin/Downloads/23DTHB4-N5-LTMMT%20(1)/DATABASE/friends.json): Dữ liệu cấu trúc ánh xạ bạn bè giữa các tài khoản thử nghiệm.
   - [`messages.json`](file:///c:/Users/Admin/Downloads/23DTHB4-N5-LTMMT%20(1)/DATABASE/messages.json): Lịch sử tin nhắn chat thử nghiệm có mã hóa.
   - [`transfers.json`](file:///c:/Users/Admin/Downloads/23DTHB4-N5-LTMMT%20(1)/DATABASE/transfers.json): Nhật ký mẫu các phiên truyền tệp tin P2P và Relay với đầy đủ các trạng thái.
   - [`client_config.json`](file:///c:/Users/Admin/Downloads/23DTHB4-N5-LTMMT%20(1)/DATABASE/client_config.json): File cấu hình phía client phục vụ kết nối tự động.

3. **Gom gọn và tái cấu trúc thư mục Database**:
   - Xóa bỏ file `database.sql` trùng lặp ở thư mục gốc.
   - Tập trung toàn bộ tài nguyên cơ sở dữ liệu vào thư mục `DATABASE/`.

### 1.2. Module nào bị ảnh hưởng?
- **Module Server & StorageEngine (`dammtp2p.server`)**: Tương thích hoàn toàn với cấu trúc file dữ liệu trong `server_data` và bảng CSDL SQL.
- **Module Xác thực (`dammtp2p.server.AuthService`)**: Đồng bộ cấu trúc tài khoản, trường `username`, `salt`, `hash`.
- **Module Truyền tệp (`dammtp2p.server.ServerFileRelayManager`)**: Đồng bộ lược đồ `file_transfers` phục vụ resume download và checksum SHA-256.
- **Module Chat & Giao diện Client**: Đồng bộ cấu hình và định dạng tin nhắn.

### 1.3. Có thay đổi API / DB / UI không?
- **API**: Không thay đổi giao thức gói tin mạng.
- **DB**: Cung cấp đầy đủ lược đồ quan hệ 9 bảng và bộ JSON mẫu cho hệ thống.
- **UI**: Không thay đổi mã nguồn giao diện.

---

## 2. Minh chứng kiểm tra

### 2.1. Môi trường và Lệnh kiểm tra
- Môi trường: Windows PowerShell / Git
- Kiểm tra tính toàn vẹn cú pháp SQL:
  ```bash
  sqlite3 test_db.sqlite < DATABASE/database.sql
  ```
- Kiểm tra tính hợp lệ của các file JSON:
  ```powershell
  Get-ChildItem DATABASE/*.json | ForEach-Object { 
      Get-Content $_.FullName -Raw | ConvertFrom-Json | Out-Null
      Write-Host "$($_.Name): VALID JSON"
  }
  ```

### 2.2. Kết quả kiểm tra
- Toàn bộ cú pháp trong [`DATABASE/database.sql`](file:///c:/Users/Admin/Downloads/23DTHB4-N5-LTMMT%20(1)/DATABASE/database.sql) được thực thi chuẩn xác, tạo đủ 9 bảng và các chỉ mục không phát sinh lỗi.
- Tất cả các file JSON (`accounts.json`, `friends.json`, `messages.json`, `transfers.json`, `client_config.json`) đều có cú pháp hợp lệ:
  - `accounts.json`: VALID JSON
  - `client_config.json`: VALID JSON
  - `friends.json`: VALID JSON
  - `messages.json`: VALID JSON
  - `transfers.json`: VALID JSON
- Thư mục gốc dự án đã được dọn sạch, toàn bộ file database nằm tập trung tại thư mục `DATABASE/`.

---

## 3. Checklist
- [x] Đã gom toàn bộ file liên quan đến cơ sở dữ liệu vào thư mục `DATABASE/`
- [x] Đã xóa bỏ các file database trùng lặp ở thư mục gốc
- [x] Đã kiểm tra cú pháp kịch bản SQL và định dạng JSON
- [x] Tuân thủ quy tắc commit theo chuẩn
- [x] Sẵn sàng tạo Pull Request từ `feature/2380600292-issue-19-database-full` vào nhánh `main`
- [ ] Review bởi thành viên nhóm
