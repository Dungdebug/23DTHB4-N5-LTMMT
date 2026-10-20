# Báo cáo mô tả công việc - Lõi Backend Server & Truyền Tải Mạng (Issue #17)

- **Nhánh thực hiện:** `feature/2380600401-issue-17-code-backend-hoan-chinh`
- **Người thực hiện:** Trần Thái Dương - MSSV: 2380600401
- **Nội dung task:** Thiết kế, xây dựng và hoàn thiện toàn bộ hệ thống lõi Backend Server Socket TCP/UDP, Relay truyền tệp dung lượng lớn và điều phối phiên mạng cho ứng dụng DAMMTP2P Chat & File Transfer

---

## 1. Mô tả thay đổi

### 1.1. Đã làm gì?
1. **Xây dựng máy chủ trung tâm TCP Control Channel (Port 8888):**
   - Lớp `ServerCore.java` và `ServerMain.java`: Lắng nghe trên tất cả giao diện mạng (`0.0.0.0`), tương thích hoàn toàn mạng ảo ZeroTier (`10.250.x.x`), LAN và Localhost.
   - Quản lý phiên đa luồng `SessionManager.java` & `ClientSession.java`: Tự động định danh người dùng, theo dõi trạng thái Online/Offline thời gian thực, xử lý ngắt kết nối an toàn.
   - Xác thực danh tính người dùng `AuthService.java`: Băm mật khẩu chuẩn PBKDF2-HMAC-SHA256 (10,000 vòng lặp) kèm chuỗi muối (Salt) 16-byte ngẫu nhiên.
   - Quản lý phòng chat đa kênh `RoomManager.java`: Hỗ trợ phòng mặc định (`Lobby`, `General`, `Voice_Lounge`, `MMT_NHOM05`), cơ chế phát tán tin nhắn Broadcast và phân phối khóa phòng AES-128-GCM.

2. **Xây dựng kênh tiếp sức truyền tệp nhị phân tốc độ cao (Port 8889):**
   - Lớp `ServerFileRelayManager.java`: Thiết lập kênh truyền riêng biệt qua Socket nhị phân TCP, không làm nghẽn kênh điều khiển chat.
   - Hỗ trợ truyền tệp dung lượng lớn lên đến 20 GiB (sử dụng 64-bit integer / `long`).
   - Phân đoạn dữ liệu (Streaming Chunk) 64 KiB kèm thuật toán mã hóa khối AES-GCM và kiểm tra tính toàn vẹn đầu cuối bằng mã băm SHA-256 (`HashVerifier.java`).
   - Cơ chế Resume đứt kết nối: Tự động ghi nhớ và đàm phán lại byte offset đã truyền khi mạng gặp sự cố.

3. **Xây dựng máy chủ phát tán âm thanh giọng nói UDP Voice Relay (Port 8890):**
   - Lớp `VoiceServerRelay.java`, `VoicePacket.java`: Thu và phát tán các gói tin âm thanh UDP theo phòng thoại thời gian thực, độ trễ cực thấp.

4. **Động cơ lưu trữ dữ liệu bền vững (StorageEngine):**
   - Lớp `StorageEngine.java`: Tự động ghi log kiểm toán (`audit.log`), lưu trữ lịch sử tin nhắn phòng và tin nhắn 1-1 riêng tư, tự động khôi phục dữ liệu và trạng thái truyền tệp sau khi khởi động lại server.

5. **Giao diện điều khiển máy chủ độc lập (Server Mission Control):**
   - Lớp `ServerFrame.java` và `ServerDashboardPanel.java`: Bảng điều khiển quản trị trực quan với 5 chỉ số KPI, tự động phát hiện IP ZeroTier, theo dõi danh sách người dùng trực tuyến và tiến độ truyền tệp thời gian thực.

6. **Kịch bản biên dịch và thực thi độc lập:**
   - Kịch bản biên dịch Java thuần UTF-8 `build.bat`, cấu hình Ant `build.xml`, tệp `manifest.mf` (`Main-Class: dammtp2p.server.ServerMain`).
   - Kịch bản chạy nhanh Server `CHAY_SERVER.bat`.

### 1.2. Module bị ảnh hưởng
- `dammtp2p.server.*`: Toàn bộ các lớp quản trị máy chủ, điều phối phiên, truyền file và lưu trữ.
- `dammtp2p.voice.*`: Kênh tiếp sức âm thanh UDP VoiceServerRelay.
- `dammtp2p.security.*`: Lớp tiện ích mã hóa CryptoUtils.
- `dammtp2p.core.*`: Giao thức mạng, định dạng gói tin PacketHeader/PacketType và mô hình dữ liệu.
- `dammtp2p.util.*`: Cấu hình mạng, phân tích IP ZeroTier và định dạng dữ liệu.

### 1.3. Có ảnh hưởng đến các module khác không?
- **Không.** Lõi Backend hoạt động độc lập theo kiến trúc Client-Server. Nhánh này không can thiệp, không sửa đổi hay ghi đè lên các tệp giao diện người dùng (Client UI) hay CSDL của các thành viên khác trong nhóm.

---

## 2. Hình ảnh minh chứng kết quả chạy thành công

- Đã biên dịch toàn bộ mã nguồn Backend thành công bằng `build.bat`.
- Server đã khởi chạy thực tế, tự động bind trên cổng `8888` (Control), `8889` (File Relay), `8890` (Voice UDP) và nhận diện IP ZeroTier `10.250.15.6`.
- Đã vượt qua 100% các bài kiểm tra tự động (Phase 4 Storage Engine, Phase 11A TCP Concurrency, Boundary Test giới hạn truyền file 20 GiB).

---

## 3. Checklist tự kiểm tra
- [x] Đã chạy build thành công bằng `build.bat` (không có lỗi biên dịch).
- [x] Đã kiểm thử chức năng khởi chạy Server ở cả 2 chế độ: Console headless và Swing Mission Control.
- [x] Đã lọc sạch toàn bộ file thừa / rác (.jar, .exe, logs runtime).
- [x] Đã tạo tệp mô tả công việc chi tiết theo đúng quy định của môn học.
