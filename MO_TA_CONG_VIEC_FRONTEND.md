# MÔ TẢ CÔNG VIỆC FRONTEND — DỰ ÁN DAMMTP2P

> **Người thực hiện:** Thành viên phụ trách Issue #6 — Giao diện chat, phòng, bạn bè  
> **Nhánh phát triển:** `feature/2380601161-issue-6-giaodien-chat-phong-banbe`  
> **Công nghệ:** Java Swing, FlatLaf (FlatDarkLaf / FlatLightLaf), Java2D (Graphics2D)

---

## 1. Tổng Quan

Toàn bộ phần Frontend bao gồm **20 file `.java`** phân bổ trong 4 package, xây dựng giao diện người dùng hoàn chỉnh cho ứng dụng truyền thông đa phương tiện DAMMTP2P — từ màn hình đăng nhập, giao diện chat phòng/bạn bè, truyền tệp P2P, phòng thoại Voice, cho đến bảng giám sát Server.

### Tổng quan kiến trúc Frontend

```
┌───────────────────────────────────────────────────────────────────────────┐
│                        ClientMainFrame (JFrame)                         │
│ ┌──────────┬───────────────────────┬──────────────────┬───────────────┐  │
│ │Workspace │  ConversationSidebar  │ ConversationTime │ Participant   │  │
│ │NavRail   │  ┌─ Tab Hội thoại    │ line             │ ListPanel     │  │
│ │          │  ├─ Tab Nhóm         │ ┌─ ChatMessage   │ ┌─ Members    │  │
│ │(Thanh    │  ├─ Tab Phòng thoại  │ │  View (x N)    │ │  + Badge    │  │
│ │điều      │  └─ Voice Roster     │ │  (bong bóng    │ │  "Đang nói" │  │
│ │hướng     │     + Badge unread   │ │  chat, avatar,  │ │             │  │
│ │dọc)      │                      │ │  thời gian)     │ │             │  │
│ │          │                      │ └────────────────│ │             │  │
│ │          │                      │ [Composer Area]  │ │             │  │
│ └──────────┴───────────────────────┴──────────────────┴───────────────┘  │
│ ┌─ AuthenticationPanel (CardLayout: Auth ↔ App) ──────────────────────┐  │
│ └─────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────┘

Popup / Dialog:
  ├─ CreateRoomDialog        ├─ SettingsDialog       ├─ AboutDialog
  ├─ IncomingFileDialog      ├─ TransferCenterDialog ├─ WorkspaceServerDialog
  ├─ ToastNotification       └─ FileDropTarget (Drag & Drop listener)

Theme Engine:
  ├─ ThemeManager (màu sắc, gradient, Light/Dark mode, createAvatarWithChatBubble)
  └─ AppIcons (hệ thống icon vector vẽ bằng Graphics2D)

Server UI:
  ├─ ServerFrame             └─ ServerDashboardPanel (Dashboard giám sát)
```

---

## 2. Danh Sách 20 File Frontend & Mô Tả Công Việc Chi Tiết

---

### 2.1. Giao diện phía người dùng (Client UI)

**Thư mục:** `src/dammtp2p/client/ui/` — 9 file

| # | File | Dòng code | Mô tả |
|---|------|-----------|-------|
| 1 | **ClientMainFrame.java** | ~1.403 dòng | Cửa sổ chính của ứng dụng chat |
| 2 | **AuthenticationPanel.java** | ~772 dòng | Màn hình đăng nhập |
| 3 | **ChatMessageView.java** | ~671 dòng | Thành phần hiển thị bong bóng chat |
| 4 | **ConversationSidebar.java** | ~1.380 dòng | Thanh sidebar danh sách cuộc trò chuyện |
| 5 | **ConversationTimeline.java** | ~370 dòng | Dòng thời gian trò chuyện |
| 6 | **ParticipantListPanel.java** | ~380 dòng | Bảng danh sách thành viên phòng |
| 7 | **TransferCenterDialog.java** | ~408 dòng | Trung tâm quản lý truyền tệp |
| 8 | **WorkspaceNavRail.java** | ~230 dòng | Thanh điều hướng dọc |
| 9 | **WorkspaceServerDialog.java** | ~30 dòng | Hộp thoại cấu hình IP/Port |

#### 2.1.1. `ClientMainFrame.java` — Cửa sổ giao diện chính

**Vai trò:** Khung tổng hợp (JFrame) kết nối tất cả thành phần: Sidebar, Timeline, Navigation Rail, Participant List.

**Công việc đã làm:**
- Xây dựng layout tổng thể bằng `BorderLayout` kết hợp `CardLayout` để chuyển đổi giữa màn hình xác thực (`CARD_AUTH`) và màn hình ứng dụng chính (`CARD_APP`).
- Thiết kế **Application Bar** phía trên: logo thương hiệu DAMMTP2P, ô tìm kiếm nhanh, trạng thái kết nối (online/offline), nút cài đặt và giới thiệu.
- Tích hợp **Conversation Sidebar** bên trái hiển thị danh sách DM, phòng chat và phòng thoại.
- Tích hợp **Conversation Timeline** ở giữa hiển thị dòng thời gian tin nhắn với composer đa dòng.
- Tích hợp **Participant List Panel** bên phải hiển thị danh sách thành viên phòng (collapsible drawer).
- Xử lý sự kiện gửi tin nhắn (Enter / nút gửi), nhận tin nhắn từ `ClientCore.ClientListener`.
- Hỗ trợ gửi tệp qua menu popup và nút đính kèm, mở `TransferCenterDialog`.
- Cập nhật trạng thái kết nối, danh sách phòng, danh sách bạn bè realtime qua callback.
- Nhúng badge mã hóa AES-128-GCM trong header cuộc trò chuyện.

#### 2.1.2. `AuthenticationPanel.java` — Màn hình đăng nhập & xác thực

**Vai trò:** Gateway đăng nhập trước khi vào ứng dụng.

**Công việc đã làm:**
- Thiết kế layout hai cột (Two-column split):
  - **Cột trái:** Thương hiệu DAMMTP2P Mesh, hiển thị ZeroTier IP, nút khởi tạo Server nội bộ.
  - **Cột phải:** Form nhập IP Server, Port, Username, Password với nút "Kết nối".
- Tính năng **Ping kiểm tra kết nối** không đồng bộ (async) — hiển thị trạng thái xanh/đỏ trước khi đăng nhập.
- Nút **Copy IP** để gửi nhanh IP cho bạn bè.
- Vẽ nền gradient động bằng `LinearGradientPaint`.
- Không hỗ trợ auto-fill hay auto-login để bảo mật.

#### 2.1.3. `ChatMessageView.java` — Bong bóng chat & hiển thị tin nhắn

**Vai trò:** Render từng dòng tin nhắn trong timeline.

**Công việc đã làm:**
- Vẽ avatar hình tròn với chữ cái đầu tên, màu sắc hash theo tên người dùng (`colorForUser`).
- Hiển thị bong bóng chat phân biệt tin gửi (bên phải, màu accent) và tin nhận (bên trái, màu nền).
- Hỗ trợ **gộp tin nhắn liên tiếp** (coalescing) trong vòng 5 phút — ẩn avatar/tên nếu cùng người gửi.
- Nhãn thời gian (timestamp) dạng `HH:mm`.
- **Inline Attachment Card** cho file đính kèm: hiển thị tên file, kích thước, thanh tiến độ tải, nút chấp nhận/từ chối.
- Badge trạng thái mã hóa: biểu tượng 🔒 nếu tin nhắn được mã hóa AES-128-GCM.
- Hover copy text — click chuột phải để sao chép nội dung tin nhắn.
- Non-clipping text wrapping: tự động xuống dòng cho văn bản dài, URL, token không ngắt.

#### 2.1.4. `ConversationSidebar.java` — Sidebar điều hướng & danh sách cuộc trò chuyện

**Vai trò:** Thanh điều hướng bên trái quản lý tất cả cuộc trò chuyện.

**Công việc đã làm:**
- Xây dựng **hệ thống tab phân đoạn** (Segmented Tabs): `TAB_DMS` (Hội thoại 1-1), `TAB_ROOMS` (Nhóm/Phòng chat), `TAB_VOICE` (Phòng thoại).
- **Tab Hội thoại (DMs):** Danh sách bạn bè online với avatar, tên, nút "Nhắn tin mới".
- **Tab Nhóm (Rooms):** Danh sách phòng chat text với icon `#`, nút "+ Tạo nhóm".
- **Tab Phòng thoại (Voice):**
  - Danh sách phòng voice với icon loa.
  - **Voice Roster** — Hiển thị danh sách thành viên đang trong phòng thoại theo thời gian thực.
  - Badge **"Đang nói 🔊"** với hiệu ứng sóng âm khi user đang phát biểu.
  - Cập nhật động khi người dùng vào/ra phòng.
- Custom Cell Renderers:
  - `TextChannelRenderer` — Render kênh chat text với icon `#` và căn lề chuẩn.
  - `VoiceChannelRenderer` — Render kênh voice với icon loa và số người tham gia.
  - `FriendUserRenderer` — Render mục bạn bè với avatar tròn, tên, trạng thái online.
- **Badge thông báo tin nhắn chưa đọc** — Bong bóng đỏ hiển thị số tin chưa đọc trên avatar/tab.
- Thanh điều khiển âm thanh phía dưới: nút Mic, Tai nghe, Ngắt kết nối voice.
- **Căn lề** (Alignment): Sửa toàn bộ padding, margin để danh sách hiển thị ngay ngắn, thống nhất.

#### 2.1.5. `ConversationTimeline.java` — Dòng thời gian trò chuyện

**Vai trò:** Khung cuộn hiển thị lịch sử tin nhắn.

**Công việc đã làm:**
- Hiển thị danh sách tin nhắn cuộn theo thời gian từ cũ đến mới.
- Tự động cuộn xuống tin nhắn mới nhất khi nhận tin.
- Gọi `ChatMessageView` để render từng tin nhắn.
- Header hiển thị tên phòng/người chat và badge bảo mật (AES-128-GCM ✓).

#### 2.1.6. `ParticipantListPanel.java` — Bảng thành viên phòng chat

**Vai trò:** Drawer bên phải hiển thị thành viên trong phòng.

**Công việc đã làm:**
- Danh sách thành viên với avatar, tên, trạng thái online/offline.
- Badge **"Đang nói 🔊"** cho voice — hiển thị ai đang phát biểu trong phòng thoại.
- Badge **bong bóng đỏ số tin nhắn chưa đọc** trên avatar thành viên (`createAvatarWithChatBubble`).
- Số đếm thành viên online (VD: "3 thành viên online").
- Nút hành động: Chat riêng, Gửi file, Xem hồ sơ.

#### 2.1.7. `TransferCenterDialog.java` — Trung tâm quản lý truyền tệp

**Vai trò:** Hộp thoại chuyên dụng giám sát và quản lý truyền tệp P2P.

**Công việc đã làm:**
- **4 KPI Telemetry Cards** hiển thị số liệu truyền tệp:
  - Tốc độ truyền (MB/s)
  - Dung lượng đã truyền / tổng (hỗ trợ đến 20 GiB)
  - Thời gian còn lại (ETA)
  - Trạng thái kiểm tra SHA-256 Checksum
- Bảng quản lý phiên truyền tệp: tên file, kích thước, người gửi/nhận, tiến độ %, trạng thái.
- Nút **Gửi file nhanh**: chọn người nhận, chọn file, checkbox mã hóa.
- Nút **Dừng/Tiếp tục/Hủy** truyền tệp.
- Thanh tiến độ (JProgressBar) với hiệu ứng gradient.

#### 2.1.8. `WorkspaceNavRail.java` — Thanh điều hướng biểu tượng dọc

**Vai trò:** Rail bar bên trái cùng để chuyển nhanh giữa các workspace/chế độ.

**Công việc đã làm:**
- Thanh dọc gồm các biểu tượng: Chat, Server Dashboard, Transfer Center, Settings, About.
- Hiệu ứng hover highlight và active state.
- Vẽ icon vector bằng Graphics2D thông qua `AppIcons`.
- Callback `NavRailListener` để `ClientMainFrame` chuyển đổi layout.

#### 2.1.9. `WorkspaceServerDialog.java` — Hộp thoại cấu hình kết nối

**Vai trò:** Dialog nhỏ nhập nhanh IP và Port server.

**Công việc đã làm:**
- Form nhập IP và Port đơn giản.
- Validate đầu vào trước khi kết nối.

---

### 2.2. Các thành phần & Hộp thoại dùng chung (Shared UI Components)

**Thư mục:** `src/dammtp2p/ui/components/` — 7 file

| # | File | Dòng code | Mô tả |
|---|------|-----------|-------|
| 10 | **ModernDialog.java** | ~220 dòng | Khung hộp thoại bo góc cơ sở |
| 11 | **CreateRoomDialog.java** | ~250 dòng | Popup tạo phòng chat mới |
| 12 | **IncomingFileDialog.java** | ~350 dòng | Thông báo nhận file P2P |
| 13 | **FileDropTarget.java** | ~90 dòng | Kéo-thả tệp vào chat |
| 14 | **SettingsDialog.java** | ~280 dòng | Cài đặt âm thanh & giao diện |
| 15 | **ToastNotification.java** | ~160 dòng | Toast thông báo nổi |
| 16 | **AboutDialog.java** | ~150 dòng | Thông tin nhóm & phiên bản |

#### 2.2.1. `ModernDialog.java` — Base Dialog bo góc hiện đại

**Công việc đã làm:**
- Xây dựng lớp cơ sở `ModernDialog` kế thừa JDialog với bo góc (rounded corners).
- Header gradient, nút đóng tùy chỉnh, nền trong suốt.
- Tất cả dialog khác (CreateRoom, IncomingFile, Settings, About) đều kế thừa từ lớp này.

#### 2.2.2. `CreateRoomDialog.java` — Hộp thoại tạo phòng chat

**Công việc đã làm:**
- Form nhập tên phòng với validation.
- Dropdown chọn loại phòng (Text / Voice).
- Nút "Tạo phòng" với hiệu ứng hover.

#### 2.2.3. `IncomingFileDialog.java` — Hộp thoại nhận file P2P

**Công việc đã làm:**
- Thông báo popup khi có người gửi file đến, hiển thị: tên file, kích thước, người gửi.
- Nút **Chấp nhận** / **Từ chối**.
- Chọn thư mục lưu file (JFileChooser).
- Hiển thị icon theo loại file (ảnh, tài liệu, nén, khác).

#### 2.2.4. `FileDropTarget.java` — Kéo-thả tệp tin (Drag & Drop)

**Công việc đã làm:**
- Lắng nghe sự kiện kéo-thả (`DropTarget`) trực tiếp vào giao diện chat.
- Khi kéo file vào → highlight vùng drop, thả → gửi file tự động.
- Hỗ trợ multi-file drop.

#### 2.2.5. `SettingsDialog.java` — Hộp thoại cài đặt

**Công việc đã làm:**
- Tab **Âm thanh:** Chọn thiết bị micro/loa, điều chỉnh âm lượng.
- Tab **Mạng:** Cấu hình port, timeout.
- Tab **Giao diện:** Chuyển đổi Light Mode / Dark Mode.
- Lưu cấu hình vào `AppConfig`.

#### 2.2.6. `ToastNotification.java` — Thông báo nổi (Toast)

**Công việc đã làm:**
- Khung thông báo nhỏ nổi ở góc dưới bên phải màn hình.
- Hiệu ứng fade-in / fade-out tự động sau 3–5 giây.
- Hỗ trợ nhiều loại: SUCCESS (xanh lá), WARNING (vàng), ERROR (đỏ), INFO (xanh dương).
- Hiển thị khi: nhận file mới, người dùng vào/ra phòng, mất kết nối, v.v.

#### 2.2.7. `AboutDialog.java` — Hộp thoại giới thiệu

**Công việc đã làm:**
- Hiển thị thông tin nhóm phát triển: tên nhóm, thành viên, MSSV.
- Phiên bản phần mềm và logo DAMMTP2P.
- Link đến repository GitHub.

---

### 2.3. Bộ chủ đề & Biểu tượng (Theme & Icons)

**Thư mục:** `src/dammtp2p/ui/theme/` — 2 file

| # | File | Dòng code | Mô tả |
|---|------|-----------|-------|
| 17 | **ThemeManager.java** | ~534 dòng | Quản lý theme, màu sắc, gradient |
| 18 | **AppIcons.java** | ~950 dòng | Hệ thống icon vector Graphics2D |

#### 2.3.1. `ThemeManager.java` — Quản lý chủ đề giao diện

**Công việc đã làm:**
- Định nghĩa **bảng màu DAMMTP2P** hoàn chỉnh (~30 hằng số màu):
  - Nền: `BG_DARK (#13161E)`, `BG_SIDEBAR (#181C26)`, `BG_NAV (#0F1117)`, `BG_CARD (#1E2330)`, `BG_ELEVATED (#232938)`.
  - Accent: `ACCENT_PRIMARY (#0D9488 Teal)`, `ACCENT_CYAN (#06B6D4)`.
  - Trạng thái: `SUCCESS (#10B981)`, `WARNING (#F59E0B)`, `DANGER (#EF4444)`.
  - Văn bản: `TEXT_PRIMARY`, `TEXT_SECONDARY`, `TEXT_MUTED`.
- Hàm `applyTheme()` — Áp dụng FlatLaf (FlatDarkLaf / FlatLightLaf) với fallback Nimbus/System.
- Hàm `toggleTheme()` — Chuyển đổi Light/Dark mode runtime.
- Hàm `createAvatarPanel()` — Tạo avatar tròn với chữ cái đầu, màu hash theo tên.
- Hàm **`createAvatarWithChatBubble()`** — Tạo avatar kèm bong bóng đỏ hiển thị số tin nhắn chưa đọc (badge notification).
- Hàm `createGradientBorder()` — Tạo border gradient cho các component.
- Hàm `createRoundedPanel()` — Panel bo góc tùy chỉnh.
- Hàm `colorForUser()` — Sinh màu avatar ổn định từ hash tên người dùng.

#### 2.3.2. `AppIcons.java` — Hệ thống biểu tượng vector

**Công việc đã làm:**
- Vẽ toàn bộ **hệ thống icon vector** bằng `Graphics2D` (không dùng file ảnh):
  - **Chat:** icon gửi tin (Send), đính kèm (Attach), emoji, trích dẫn.
  - **Voice:** icon micro, tai nghe, loa, sóng âm, ngắt kết nối.
  - **File:** icon tải lên, tải xuống, thư mục, loại file (ảnh, tài liệu, nén).
  - **Navigation:** icon cài đặt, giới thiệu, dashboard, thoát.
  - **Trạng thái:** icon online (xanh), offline (xám), busy (đỏ), away (vàng).
  - **Bảo mật:** icon khóa (mã hóa AES), shield (xác minh).
- Tất cả icon hỗ trợ resize và thay đổi màu theo theme Light/Dark.

---

### 2.4. Giao diện giám sát máy chủ (Server UI)

**Thư mục:** `src/dammtp2p/server/ui/` — 2 file

| # | File | Dòng code | Mô tả |
|---|------|-----------|-------|
| 19 | **ServerFrame.java** | ~100 dòng | Cửa sổ ứng dụng Server |
| 20 | **ServerDashboardPanel.java** | ~413 dòng | Dashboard giám sát Server |

#### 2.4.1. `ServerFrame.java` — Cửa sổ Server

**Công việc đã làm:**
- JFrame chứa `ServerDashboardPanel`.
- Xử lý sự kiện đóng cửa sổ (shutdown server an toàn).
- Có thể chạy độc lập hoặc nhúng trong `ClientMainFrame`.

#### 2.4.2. `ServerDashboardPanel.java` — Bảng điều khiển giám sát

**Công việc đã làm:**
- **KPI Cards:** Hiển thị trạng thái server, ZeroTier IP, Control Port (8888), File Port (8889), số client online.
- **Bảng Client Online:** Danh sách user đang kết nối — tên, IP, thời gian kết nối.
- **Bảng Phiên Truyền Tệp:** Theo dõi các phiên file relay đang hoạt động.
- **Log hoạt động:** TextArea cuộn hiển thị log realtime (kết nối, ngắt kết nối, lỗi).
- Nút **Copy IP** để chia sẻ nhanh IP server cho client.
- Implement `ServerCore.ServerEventListener` để cập nhật giao diện realtime.

---

## 3. Tổng Kết Kỹ Thuật Frontend

### 3.1. Công nghệ & Pattern sử dụng

| Công nghệ | Mục đích |
|-----------|----------|
| **Java Swing** | Framework UI chính |
| **FlatLaf** | Look & Feel hiện đại (Dark/Light mode) |
| **Java2D (Graphics2D)** | Vẽ icon vector, gradient, bo góc, avatar |
| **CardLayout** | Chuyển đổi màn hình (Auth ↔ App) |
| **BorderLayout + BoxLayout** | Bố cục tổng thể responsive |
| **Observer/Listener Pattern** | Giao tiếp giữa các component (`ClientListener`, `SidebarListener`, `NavRailListener`) |
| **Custom Cell Renderer** | Hiển thị danh sách tùy chỉnh (`JList`) |
| **SwingUtilities.invokeLater** | Đảm bảo thread-safety khi cập nhật UI |

### 3.2. Các tính năng Frontend nổi bật

1. **Giao diện Dark Mode** mặc định với bảng màu Teal & Cyan signature — phong cách chuyên nghiệp.
2. **Voice Roster realtime** — Hiển thị ai đang trong phòng thoại, badge "Đang nói 🔊" với hiệu ứng sóng âm.
3. **Badge thông báo tin nhắn chưa đọc** — Bong bóng đỏ trên avatar, tự xóa khi đã đọc.
4. **Inline File Transfer Card** — Theo dõi tiến độ truyền tệp ngay trong chat.
5. **Drag & Drop** gửi file — Kéo thả file trực tiếp vào giao diện chat.
6. **Toast Notification** — Thông báo nổi không chặn giao diện.
7. **Icon vector 100%** — Không dùng file ảnh, toàn bộ icon vẽ bằng Graphics2D, scale tốt mọi độ phân giải.
8. **KPI Telemetry Dashboard** — Server dashboard hiển thị số liệu hoạt động realtime.

### 3.3. Thống kê mã nguồn Frontend

| Nhóm | Số file | Tổng dòng code (ước tính) |
|------|---------|---------------------------|
| Client UI (`client/ui/`) | 9 file | ~5.644 dòng |
| Shared Components (`ui/components/`) | 7 file | ~1.500 dòng |
| Theme & Icons (`ui/theme/`) | 2 file | ~1.484 dòng |
| Server UI (`server/ui/`) | 2 file | ~513 dòng |
| **Tổng cộng** | **20 file** | **~9.141 dòng** |

---

## 4. Các File Đã Sửa/Cải Thiện Trong Issue 6

Ngoài việc xây dựng từ đầu, các file sau đã được **sửa lỗi và cải thiện** trong quá trình phát triển Issue 6:

| File | Cải thiện |
|------|-----------|
| `ConversationSidebar.java` | Sửa căn lề danh sách bạn bè, thêm Voice Roster realtime, thêm badge tin nhắn chưa đọc |
| `ParticipantListPanel.java` | Thêm badge "Đang nói" cho voice, thêm bong bóng đỏ tin nhắn chưa đọc trên avatar |
| `ThemeManager.java` | Thêm hàm `createAvatarWithChatBubble()` cho badge notification |
| `.gitignore` | Loại bỏ `downloads/`, `server_data/`, `screenshots/`, `scripts/`, metadata IDE |

---

> **Ghi chú:** Sẽ demo trực tiếp toàn bộ tính năng Frontend trong buổi báo cáo.
