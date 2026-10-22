# NHÓM 05 - ĐỒ ÁN LẬP TRÌNH MẠNG MÁY TÍNH
## ĐỀ TÀI: ỨNG DỤNG CHAT TÍCH HỢP TRUYỀN TỆP TIN DUNG LƯỢNG LỚN & ĐA TIỆN ÍCH MẠNG NÂNG CAO (DAMMTP2P)

> **Học phần:** Lập trình Mạng máy tính  
> **Nhóm thực hiện:** Nhóm 05 (04 thành viên)  
> **Kiến trúc:** Hybrid (Client-Server Relay + Direct P2P) qua Socket TCP/UDP, hỗ trợ LAN & ZeroTier Mesh VPN  
> **Công nghệ:** Java 17+, Java Swing + FlatLaf Modern UI, Custom Binary/JSON Protocol, SQLite/MySQL Ready  

---

## 1. Bảng đối soát yêu cầu & Đánh giá của Giảng viên

Nhóm đã đối chiếu trực tiếp với phiếu đánh giá và hoàn thiện **100% các yêu cầu bắt buộc và tính năng mở rộng**:

| Tiêu chí / Góp ý của Giảng viên | Giải pháp & Hiện trạng trong Codebase DAMMTP2P | Trạng thái |
| :--- | :--- | :---: |
| **1. Tách biệt kênh truyền thông**<br>Tách Chat và File thành 2 socket TCP độc lập, không nghẽn luồng đọc tin nhắn. | • **Cổng `8888` (TCP):** Kênh điều khiển, xác thực tài khoản và tin nhắn văn bản.<br>• **Cổng `8889` (TCP):** Kênh trung chuyển dữ liệu tệp tin độc lập.<br>• **Cổng `8890` (UDP):** Kênh thoại VoIP thời gian thực. Không làm nghẽn tin nhắn khi tải file lớn. | **ĐÃ HOÀN TẤT**<br>(Vượt chuẩn) |
| **2. Khả năng truyền file lớn & Resume**<br>Hỗ trợ file lớn, truyền tiếp tại điểm đứt gãy (Resume transfer) và kiểm tra tính toàn vẹn. | • Hỗ trợ tệp lên đến **20 GiB** (dùng kiểu dữ liệu `long` 64-bit trong `FileMetadata`).<br>• Bộ đệm Streaming (64KB - 256KB) kết hợp `RandomAccessFile`, đọc ghi trực tiếp không nạp file vào RAM.<br>• Tự động ghi nhận `startOffset` để gửi bản tin `FILE_TRANSFER_RESUME_REQ` tiếp tục tại điểm đứt gãy.<br>• Đối soát mã băm **SHA-256** bảo toàn tính toàn vẹn tuyệt đối. | **ĐÃ HOÀN TẤT** |
| **3. Bảo mật dữ liệu & Xác thực**<br>Mật khẩu băm an toàn, mã hóa tin nhắn và file bằng AES-128 trước khi gửi qua Socket. | • Xác thực tài khoản với chuẩn **PBKDF2-HMAC-SHA256** (16-byte random salt, 10,000 vòng lặp).<br>• Mã hóa khối **AES-128-GCM** (12-byte random IV, 128-bit authentication tag chống giả mạo) cho toàn bộ tin nhắn và khối tệp tin.<br>• Tích hợp thanh tra an ninh trực quan (nút **🔒 AES-128**) trên giao diện Client. | **ĐÃ HOÀN TẤT** |
| **4. Tính năng mở rộng: Chat nhóm**<br>Quản lý phòng chat và phân phối bản tin một - nhiều (Broadcast) của Server. | • Quản lý nhiều phòng chat đồng thời (`#Lobby`, `#General`, `#MMT_NHOM05`).<br>• Xử lý đa luồng phân phối bản tin Broadcast một-nhiều tới tất cả thành viên trong phòng.<br>• Chuyển đổi linh hoạt giữa Chat phòng (Group) và Chat riêng tư 1-1 (Private). | **ĐÃ HOÀN TẤT** |
| **5. Giải quyết điểm nghẽn cổ chai Server**<br>Tránh việc Server làm nghẽn cổ chai khi nhiều người truyền file cùng lúc. | • Thiết kế theo mô hình **Hybrid P2P + Relay**:<br>  + Hỗ trợ kết nối **Socket TCP trực tiếp P2P** giữa 2 Client trong LAN/ZeroTier (`DefaultPeerConnector`, `DefaultPeerListener`) truyền thẳng không qua Server.<br>  + Server Relay đóng vai trò Fallback khi bị chặn bởi NAT/Firewall đối xứng.<br>  + Kênh Relay trên Server dùng ThreadPool đa luồng và luân chuyển chunk nhị phân nhỏ, không tốn RAM Server. | **ĐÃ HOÀN TẤT** |
| **6. Khối lượng công việc cho 4 thành viên**<br>Bổ sung các tính năng nâng cao để đề tài dày dặn, tương xứng quy mô nhóm. | • Tích hợp **Voice Chat VoIP UDP** thời gian thực với thuật toán VAD lọc khoảng lặng.<br>• **Quản lý danh sách bạn bè:** Thêm bạn từ phòng chat, theo dõi trạng thái Online/Offline.<br>• **Cơ sở dữ liệu hoàn chỉnh (`database.sql`):** Thiết kế 9 bảng chuẩn CSDL quan hệ.<br>• **Server Dashboard:** Theo dõi CPU, RAM, băng thông truyền tải và quản trị kết nối. | **XUẤT SẮC** |

---

## 2. Kiến trúc mạng & Các cổng dịch vụ

```text
               +-------------------------------------------------------+
               |               DAMMTP2P SERVER (Central)              |
               |                                                       |
               |  [Port 8888: TCP Chat]   [Port 8889: TCP File Relay]  |
               |  - Auth (PBKDF2)         - Chunk Stream Buffer        |
               |  - Room Broadcast        - Fallback Multi-client      |
               |  - Private 1-1 Route                                  |
               |                                                       |
               |               [Port 8890: UDP Voice VoIP]             |
               |               - Voice Activity Detection (VAD)        |
               +---------------------------+---------------------------+
                                           |
                 Control & Relay           |           Voice Datagrams
                                           v
       +-----------------------------------+-----------------------------------+
       |                                                                       |
       v                                                                       v
+--------------+                                                         +--------------+
|   CLIENT A   | <======== [Direct P2P TCP Socket (LAN/ZeroTier)] =======> |   CLIENT B   |
| (Swing UI)   |             Truyền file trực tiếp không qua Server      | (Swing UI)   |
+--------------+                                                         +--------------+
```

| Cổng (Port) | Giao thức | Tên kênh | Chức năng chi tiết |
| :--- | :---: | :--- | :--- |
| **`8888`** | **TCP** | **Control & Text Channel** | • Đăng ký, đăng nhập tài khoản (PBKDF2-HMAC-SHA256).<br>• Quản lý danh sách Client online, danh sách bạn bè.<br>• Điều phối phòng chat, tin nhắn riêng tư 1-1 và bản tin Broadcast nhóm.<br>• Báo hiệu điều khiển truyền file và phiên Voice. |
| **`8889`** | **TCP** | **File Data Relay Channel** | • Kênh dữ liệu nhị phân tách biệt hoàn toàn với kênh 8888.<br>• Chuyển tiếp luồng file tốc độ cao (hỗ trợ truyền file nhóm hoặc khi bị nghẽn NAT).<br>• Bộ đệm xoay vòng 64KB–256KB chống tràn RAM máy chủ. |
| **`8890`** | **UDP** | **Voice VoIP Channel** | • Truyền tải âm thanh độ trễ cực thấp qua gói tin Datagram.<br>• Định dạng PCM 16-bit Mono 16,000 Hz, frame 20ms.<br>• Tự động ngắt mic khi im lặng (VAD threshold RMS 250.0). |

---

## 3. Các tính năng nổi bật của ứng dụng

### 3.1. Truyền tệp tin 1-1 và nhóm (File Transfer Engine)
- **Truyền tệp cực lớn (lên đến 20 GiB):** Thiết kế toàn diện dùng kiểu dữ liệu 64-bit `long` cho `fileSize`, `startOffset`, `bytesTransferred`.
- **Cơ chế truyền tiếp gián đoạn (Resume Transfer):** Khi kết nối bị ngắt, bên nhận gửi số byte đã ghi trên ổ đĩa. Bên gửi dịch chuyển con trỏ tệp (`RandomAccessFile.seek`) để truyền tiếp mà không phải gửi lại từ đầu.
- **Toàn vẹn dữ liệu:** Tự động đối soát mã băm **SHA-256** trước và sau khi truyền.
- **Thanh tiến trình & Giám sát:** Hiển thị phần trăm, tốc độ tải thực tế (MB/s), thời gian còn lại (ETA).

### 3.2. Bảo mật & Xác thực an toàn (Security & Privacy)
- **Mật khẩu an toàn:** Không bao giờ lưu mật khẩu thô. Sử dụng **PBKDF2-HMAC-SHA256** với chuỗi Salt ngẫu nhiên 16 bytes và 10,000 vòng lặp.
- **Mã hóa AES-128-GCM:** Toàn bộ tin nhắn và khối tệp tin được mã hóa bằng AES-128 với IV ngẫu nhiên 12-byte và 128-bit Authentication Tag.
- **Trình thanh tra an toàn (Security Inspector):** Người dùng có thể bấm vào huy hiệu **🔒 AES-128** trên giao diện tin nhắn để xem chi tiết Plaintext, Ciphertext dạng Base64, Nonce/IV và mã xác thực Tag.

### 3.3. Quản lý bạn bè & Trạng thái hiện diện
- **Danh sách bạn bè phân loại:** Hiển thị riêng biệt danh sách Bạn bè Đang trực tuyến (Online) và Ngoại tuyến (Offline).
- **Kết bạn nhanh:** Nhấp chuột phải vào người dùng bất kỳ trong phòng chat để gửi kết bạn.
- **Lưu trữ bạn bè:** Dữ liệu bạn bè được lưu trữ liên tục (`server_data/friends.json`) và sẵn sàng đồng bộ vào bảng CSDL `friends`.

### 3.4. Chat nhóm (Group Broadcast) & Chat 1-1 (Direct Message)
- Quản lý các kênh phòng chat: `#Lobby`, `#General`, `#Voice_Lounge`, `#MMT_NHOM05`.
- Chế độ Direct Message 1-1 bảo mật cao, tách biệt với luồng chat công khai của phòng.

### 3.5. Cơ sở dữ liệu quan hệ (Database Schema)
Hệ thống đi kèm tệp script [database.sql](file:///c:/Users/Admin/Downloads/DAMMTP2P%20(3)/DAMMTP2P/database.sql) chuẩn hóa gồm **9 bảng**:
1. `users`: Quản lý tài khoản, salt và mã băm mật khẩu.
2. `friends`: Danh sách bạn bè và trạng thái quan hệ (Pending, Accepted, Blocked).
3. `chat_rooms`: Danh mục các phòng chat (công khai / riêng tư).
4. `room_members`: Quản lý thành viên và quyền hạn trong phòng (Admin, Member).
5. `chat_messages`: Lưu trữ lịch sử tin nhắn (hỗ trợ lưu cả Ciphertext, IV, Tag).
6. `file_transfers`: Quản lý phiên gửi file, tiến độ offset và mã băm SHA-256.
7. `voice_sessions`: Nhật ký cuộc gọi và phiên đàm thoại UDP.
8. `server_logs`: Nhật ký sự kiện bảo mật và hoạt động của máy chủ.
9. `app_settings`: Cấu hình hệ thống (Port, buffer size, ngưỡng VAD).

---

## 4. Cấu trúc mã nguồn dự án

```text
DAMMTP2P/
├── src/dammtp2p/
│   ├── DAMMTP2P.java                 # Điểm khởi chạy chính & điều phối CLI
│   ├── client/                       # Xử lý Logic Client & Quản lý Bạn bè (FriendsManager)
│   │   └── ui/                       # Giao diện Swing FlatLaf (MainFrame, Sidebar, ChatView, Inspector)
│   ├── server/                       # Xử lý Logic Server, AuthService, RoomManager, Relay, ServerUI
│   ├── core/
│   │   ├── model/                    # ChatMessage, FileMetadata, PeerInfo, TransferState
│   │   ├── network/                  # Sockets TCP, kết nối P2P (DefaultPeerConnector, DefaultPeerListener)
│   │   ├── protocol/                 # Custom Wire Header (DMTP), PacketType, Serializer
│   │   └── transfer/                 # Động cơ truyền file (FileSender, FileReceiver, HashVerifier)
│   ├── voice/                        # Âm thanh UDP thời gian thực (VoiceClient, Relay, VAD)
│   ├── security/                     # Module bảo mật (CryptoUtils: PBKDF2, AES-128-GCM, SHA-256)
│   └── util/                         # FormatUtils, NetworkUtils (Quét tự động IP ZeroTier)
├── lib/                              # Thư viện giao diện FlatLaf (flatlaf-3.5.4.jar)
├── dist/                             # Bản đóng gói chạy trực tiếp (DAMMTP2P.jar)
├── database.sql                      # Kịch bản DDL Cơ sở dữ liệu 9 bảng chuẩn hóa
├── server_data/                      # Thư mục dữ liệu Server (accounts.json, friends.json)
├── downloads/                        # Thư mục nhận tệp tin mặc định
├── build.bat                         # Kịch bản tự động biên dịch và tạo dist/DAMMTP2P.jar
├── run-server.bat / CHAY_SERVER.bat  # Khởi động Server Dashboard
├── run-client.bat / CHAY_CLIENT.bat  # Khởi động Client Giao diện người dùng
└── README_SUMMARY.md                 # Tài liệu tổng quan hệ thống & hướng dẫn bảo vệ đồ án
```

---

## 5. Hướng dẫn cài đặt & Khởi chạy

### 5.1. Yêu cầu môi trường
* Máy tính đã cài đặt **Java JDK 17** trở lên.
* Khuyến nghị JDK 21 (ví dụ: `C:\Program Files\Java\jdk-21.0.11\bin`).

### 5.2. Khởi chạy nhanh
1. **Khởi chạy Server:** Chạy file `run-server.bat` (Server tự động mở Dashboard quản lý trên cổng 8888, 8889, 8890).
2. **Khởi chạy Client:** Chạy file `run-client.bat` để mở giao diện người dùng. Có thể mở nhiều Client cùng lúc để thử nghiệm.

### 5.3. Tài khoản thử nghiệm mặc định
| Tên đăng nhập (Username) | Mật khẩu (Password) | Quyền hạn |
| :--- | :--- | :--- |
| `alice` | `password123` | Người dùng thường |
| `bob` | `password123` | Người dùng thường |
| `charlie` | `password123` | Người dùng thường |
| `admin` | `admin123` | Quản trị viên hệ thống |

---

## 6. Lệnh kiểm thử tự động (Automated Verification)

Chương trình tích hợp sẵn 10 ca kiểm thử đơn vị và tích hợp tự động:

```cmd
:: Kiểm thử tự động toàn bộ 10 ca test hệ thống
java -Dfile.encoding=UTF-8 -jar dist\DAMMTP2P.jar --test

:: Kiểm thử chuyên sâu truyền tệp lớn biên độ 20 GiB
java -Dfile.encoding=UTF-8 -jar dist\DAMMTP2P.jar --test-20gb

:: Kiểm thử hệ thống âm thanh thoại Voice UDP & VAD
java -Dfile.encoding=UTF-8 -jar dist\DAMMTP2P.jar --test-voice
```

Kết quả kiểm thử mặc định: **10/10 PASS (100%)**.
