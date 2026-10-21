# Báo cáo mô tả công việc - Giao diện người dùng Frontend Client GUI (Issue #22, #23)

- **Nhánh thực hiện:** `feature/2380601161-issue-6-giaodien-chat-phong-banbe` (hợp nhất qua Pull Request #22, #23)
- **Người thực hiện:** Tô Thế Kiệt - MSSV: 2380601161
- **Nội dung task:** Thiết kế, xây dựng và hoàn thiện toàn bộ hệ thống giao diện người dùng Frontend Client GUI trên nền Java Swing & FlatLaf Modern UI, hệ thống quản lý bạn bè, phòng chat, truyền tệp trực quan và thoại VoIP cho ứng dụng DAMMTP2P Chat & File Transfer.

---

## 1. Danh sách các tệp mã nguồn Frontend phụ trách

Toàn bộ mã nguồn phía giao diện người dùng và lõi điều khiển Client được tổ chức thành các module chuẩn mực:

### 1.1. Module Giao diện chính (`dammtp2p.client.ui.*`)
1. **`ClientMainFrame.java` (Cửa sổ chính của Client):**
   - Khung giao diện trung tâm của ứng dụng với bố cục đa khung nhìn (Multi-pane).
   - Tích hợp thanh tiêu đề tuỳ biến, hiển thị tên tài khoản, avatar, trạng thái kết nối mạng và IP kết nối.
   - Phân chia 3 vùng làm việc chính: Thanh điều hướng (NavRail), Danh mục hội thoại (Sidebar), và Khung trò chuyện trung tâm (Chat View).
2. **`AuthenticationPanel.java` (Xác thực đăng nhập & Đăng ký):**
   - Màn hình khởi động hỗ trợ đăng nhập và đăng ký tài khoản mới với hiệu ứng chuyển tab mượt mà.
   - Xác thực dữ liệu đầu vào (Input validation): kiểm tra độ dài username, mật khẩu an toàn và địa chỉ Server IP/Port.
   - Tự động ghi nhớ phiên đăng nhập và đọc cấu hình từ `client_config.json`.
3. **`ChatMessageView.java` (Khung hiển thị tin nhắn):**
   - Trình bày lịch sử tin nhắn dạng bong bóng chat (Chat Bubble) bo tròn hiện đại, tách biệt giữa tin nhắn của bản thân (căn phải) và bạn bè (căn trái).
   - Hiển thị thời gian gửi, nhãn mã hóa AES-GCM và thanh tra an ninh trực quan.
   - Hỗ trợ hiển thị tệp tin đính kèm kèm kích thước và nút tải về trực tiếp trong tin nhắn.
4. **`ConversationSidebar.java` (Thanh danh mục phòng chat & Bạn bè):**
   - Danh sách phòng công khai (`#Lobby`, `#General`, `#MMT_NHOM05`) và danh sách trò chuyện 1-1 riêng tư.
   - Hiển thị chấm trạng thái thời gian thực: Xanh (Online), Xám (Offline), Xanh lá nhấp nháy (Đang nói Voice).
   - Tích hợp Badge thông báo số lượng tin nhắn chưa đọc bo tròn màu đỏ nổi bật.
5. **`ConversationTimeline.java` (Dòng thời gian & Thanh tra mã hóa):**
   - Quản lý luồng hiển thị tin nhắn theo dòng thời gian, tự động cuộn xuống cuối (Auto-scroll) khi có tin mới.
   - Nút bấm **🔒 AES-128** tích hợp bộ thanh tra an ninh (Security Inspector), cho phép người dùng xem Plaintext, Ciphertext và Vector IV 12-byte.
6. **`ParticipantListPanel.java` (Danh sách thành viên phòng):**
   - Hiển thị các thành viên đang có mặt trong phòng chat hiện tại, phân quyền Host/Admin và Member.
   - Tích hợp Menu chuột phải (Context Menu): Gửi lời mời kết bạn, Chat riêng tư 1-1, hoặc chuyển tiếp tệp tin.
7. **`TransferCenterDialog.java` (Trung tâm quản lý truyền tệp):**
   - Bảng theo dõi tiến độ gửi và nhận tệp tin thời gian thực với thanh Progress Bar, tốc độ truyền (MB/s), thời gian ước tính còn lại và số byte đã hoàn tất.
   - Nút chức năng Tạm dừng / Tiếp tục (Resume) và nút kiểm tra mã băm SHA-256 bảo toàn tính toàn vẹn.
8. **`WorkspaceNavRail.java` & `WorkspaceServerDialog.java`:**
   - Thanh điều hướng dọc thu nhỏ (Navigation Rail) chuẩn giao diện Discord/Slack.
   - Hộp thoại cấu hình và chuyển đổi máy chủ kết nối linh hoạt.

### 1.2. Module Thành phần UI tuỳ biến (`dammtp2p.ui.components.*`)
1. **`ToastNotification.java`:** Hệ thống thông báo nổi (Toast/Snack bar) tự động trượt vào góc phải màn hình khi có sự kiện (Bạn bè online, tệp tin gửi đến, cảnh báo lỗi).
2. **`FileDropTarget.java`:** Hỗ trợ tính năng kéo-thả tệp tin (Drag & Drop) trực tiếp từ File Explorer của Windows vào khung chat để gửi nhanh.
3. **`IncomingFileDialog.java`:** Hộp thoại thông báo khi có tệp tin gửi tới, hiển thị người gửi, tên tệp, dung lượng và nút Chấp nhận / Từ chối tải.
4. **`CreateRoomDialog.java`:** Hộp thoại tạo phòng chat nhóm mới, hỗ trợ đặt tên kênh và tuỳ chọn kích hoạt đàm thoại thoại Voice.
5. **`SettingsDialog.java` & `ModernDialog.java`:** Bảng cài đặt ứng dụng: chỉnh âm lượng Micro/Loa, đổi thư mục lưu file tải về (`downloads/`) và lớp nền hộp thoại phẳng bo góc.
6. **`AboutDialog.java`:** Giới thiệu thông tin đề tài môn học LTMMT và danh sách 4 thành viên nhóm 05.

### 1.3. Module Theme & Đồ họa Vector (`dammtp2p.ui.theme.*`)
1. **`ThemeManager.java`:**
   - Tích hợp bộ giao diện FlatLaf Dark Theme hiện đại, loại bỏ hoàn toàn giao diện cũ kỹ của Java Swing cổ điển.
   - Cung cấp hàm `createAvatarWithChatBubble(...)` vẽ Avatar chữ cái đầu kết hợp chấm trạng thái Online/Offline và Badge số tin nhắn chưa đọc.
2. **`AppIcons.java`:**
   - Bộ sưu tập toàn bộ biểu tượng (Icons) trong ứng dụng được vẽ bằng mã đồ họa thuần **Java2D (`Graphics2D`)**.
   - Không phụ thuộc vào tệp hình ảnh bitmap (.png, .jpg) ngoài, giúp ứng dụng nhẹ, khởi động tức thì và sắc nét ở mọi độ phân giải DPI màn hình.

### 1.4. Module Lõi điều phối Client & Âm thanh (`dammtp2p.client.*`, `dammtp2p.voice.*`)
1. **`ClientCore.java`:** Lớp trung tâm điều phối kết nối mạng Client tới Server (Port 8888 & 8889), phân phối sự kiện mạng tới các thành phần UI trên luồng Swing Event Dispatch Thread (EDT) bằng `SwingUtilities.invokeLater`.
2. **`FriendsManager.java`:** Động cơ quản lý quan hệ bạn bè, đồng bộ danh sách bạn bè với CSDL và phản hồi các thao tác kết bạn.
3. **`VoiceClient.java`, `VoiceCapture.java`, `VoicePlayback.java`:**
   - Thu âm âm thanh từ Micro (`TargetDataLine`) và phát âm thanh ra Loa (`SourceDataLine`).
   - Tích hợp bộ lọc âm VAD và đèn báo tín hiệu nói (Speaking Indicator) nhấp nháy trực tiếp trên Avatar của Sidebar.
4. **`DAMMTP2P.java`:** Điểm khởi chạy (Main Entry Point) toàn diện hỗ trợ chuyển đổi giữa chế độ Client và Server.

---

## 2. Mô tả chi tiết các thay đổi & Tính năng đã hoàn thành

### 2.1. Đã làm gì?
1. **Thiết kế giao diện hiện đại chuẩn FlatLaf Dark Theme:**
   - Xây dựng bố cục 3 cột trực quan: Navigation Rail -> Sidebar hội thoại/bạn bè -> Khung chat trung tâm.
   - Thiết lập bảng màu Dark Mode hài hoà, giảm mỏi mắt, tương thích hoàn toàn Windows 10/11.
2. **Xây dựng hệ thống Chat tương tác cao:**
   - Hỗ trợ song song: Chat phòng công khai nhóm (Broadcast) và Chat riêng tư 1-1 (Private DM).
   - Tích hợp hiển thị tin nhắn dạng bong bóng chat kèm avatar và timestamp.
   - Tích hợp thanh tra mật mã an ninh hiển thị Plaintext/Ciphertext AES-128-GCM.
3. **Hệ thống Quản lý bạn bè & Danh sách thành viên:**
   - Xem danh sách thành viên trực tuyến trong phòng, gửi lời mời kết bạn chỉ với 1 cú click chuột phải.
   - Danh sách bạn bè cập nhật trạng thái Online/Offline tức thời dựa trên gói tin thông báo từ Server.
4. **Tích hợp giao diện Truyền tệp kéo-thả (Drag & Drop):**
   - Kéo file trực tiếp từ máy tính thả vào khung chat để gửi.
   - Bảng theo dõi tiến độ chi tiết (Transfer Center) hiển thị phần trăm, tốc độ (MB/s) và nút Resume khi kết nối chập chờn.
5. **Giao diện thoại Voice VoIP UDP thời gian thực:**
   - Phím bật/tắt Micro (Mute/Unmute) và Loa (Deafen).
   - Đèn nháy xanh lá báo hiệu người dùng đang nói (Voice Activity Indicator).
6. **Kịch bản thực thi độc lập:**
   - Cung cấp file thực thi 1-click `CHAY_CLIENT.bat` và `run-client.bat`.

### 2.2. Module bị ảnh hưởng
- `dammtp2p.client.*`: Toàn bộ các lớp giao diện và điều phối phía người dùng.
- `dammtp2p.ui.*`: Hệ thống Theme FlatLaf, component tuỳ biến và bộ icon vector Java2D.
- `dammtp2p.voice.*`: Bộ thu/phát âm thanh tích hợp với giao diện.

### 2.3. Có ảnh hưởng đến các module khác không?
- **Không.** Giao diện Frontend hoạt động tuân thủ chặt chẽ kiến trúc Client-Server thông qua Wire Protocol và các gói tin chuẩn (`PacketHeader`, `PacketType`). Giao diện gọi qua các phương thức của `ClientCore` mà không can thiệp trực tiếp vào mã nguồn quản trị Server hay schema CSDL.

---

## 3. Minh chứng kết quả kiểm thử

- **Khởi động Client độc lập:** Chạy thành công thông qua `CHAY_CLIENT.bat` và `run-client.bat` trên môi trường Java 21 LTS.
- **Xác thực người dùng:** Đăng ký tài khoản mới và đăng nhập thành công với các tài khoản mẫu (`alice`, `bob`, `charlie`, `admin`).
- **Thử nghiệm đồng thời:** Mở cùng lúc 2 đến 3 cửa sổ Client, gửi nhận tin nhắn văn bản tức thời, thêm bạn bè, gửi file và hiển thị trạng thái chính xác.
- **Vệ sinh mã nguồn:** Toàn bộ file mã nguồn giao diện đã được kiểm tra, không chứa file rác nhị phân (`.class`, `.jar`, `.log`).

---

## 4. Checklist tự kiểm tra
- [x] Đã biên dịch toàn bộ mã nguồn Frontend thành công bằng `build.bat` (không có lỗi cú pháp).
- [x] Đã kiểm thử đầy đủ các chức năng: Đăng nhập/Đăng ký, Chat phòng, Chat 1-1, Kết bạn, Gửi file kéo-thả và Voice indicator.
- [x] Tuân thủ quy định vệ sinh mã nguồn, không commit các thư mục build (`dist/`, `build/`) và cấu hình cá nhân IDE (`.vscode/`, `.idea/`).
- [x] Đã lập tài liệu báo cáo mô tả công việc đầy đủ theo mẫu hướng dẫn của môn học.
