# 23DTHB4-N5-LTMMT

STT|  MSSV | Họ và tên | Công việc thực hiện | Merge Request
1.2380600292|Đỗ Tiến Dũng| Tổng duyệt, hoàn thiện code database và bổ sung code còn thiếu, hoàn thiện file README.md|#24

2.2380600401|Trần Thái Dương|Backend + realtime/socket + truyền file|#18

3.2380601152|Nguyễn Tấn Kiệt|CSDL logs/backup, dữ liệu và lịch sử|#20

4.2380601161|Tô Thế Kiệt|Frontend đăng ký, đăng nhập, frontend trong tài khoản người dùng|#23

Mô tả đề tài: Ứng dụng chat có tích hơp tính năng truyền tin 1-1

Ngôn ngữ: Java, SQL và JSON

1. Thông tin chung & Kiến trúc
Đề tài: Ứng dụng chat tích hợp truyền file dung lượng lớn và tiện ích mạng nâng cao.

Quy mô: Nhóm 05 (04 thành viên).

Công nghệ: Java 17+, FlatLaf Swing UI, Wire Protocol nhị phân/JSON tùy biến, CSDL SQLite/MySQL (9 bảng).

Mô hình kết nối: Hybrid linh hoạt kết hợp giữa Direct P2P (kết nối trực tiếp giữa 2 máy trong mạng LAN/ZeroTier để giải tỏa nghẽn cổ chai) và Client-Server Relay (dùng làm cơ chế dự phòng Fallback khi vướng NAT/Firewall).

2. Thiết kế tách kênh mạng (3 Cổng độc lập)
Cổng 8888 (TCP): Điều khiển hệ thống, xác thực người dùng, định tuyến tin nhắn riêng 1-1 và tin nhắn phòng nhóm.

Cổng 8889 (TCP): Trung chuyển dữ liệu file độc lập, dùng bộ đệm xoay vòng (64KB - 256KB) tránh nghẽn luồng chat và chống tràn RAM.

Cổng 8890 (UDP): Đàm thoại âm thanh VoIP thời gian thực (PCM 16-bit 16kHz) tích hợp thuật toán VAD tự động ngắt khi im lặng.

3. Tính năng cốt lõi & Điểm nổi bật
Truyền tệp lớn & Khôi phục (Resume): Hỗ trợ tệp tới 20 GiB (dùng con trỏ 64-bit RandomAccessFile), tự động bắt tiếp vị trí đứt gãy (startOffset) khi mất mạng cắm lại, xác thực toàn vẹn 100% bằng SHA-256.

Bảo mật chuẩn công nghiệp:

Băm mật khẩu bằng PBKDF2-HMAC-SHA256 (10.000 vòng + 16-byte Salt).

Mã hóa gói tin và khối tệp bằng AES-128-GCM (chống nghe lén và chống giả mạo Tag 128-bit).

Có trình thanh tra an ninh trực quan (Security Inspector) soi Plaintext/Ciphertext ngay trên UI.

Tiện ích mở rộng: Chat nhóm đa phòng (#Lobby, #General,...), voice chat qua UDP, kết bạn trực tiếp từ phòng chat và Server Dashboard theo dõi CPU/RAM/Băng thông trực tiếp.

Cơ sở dữ liệu: 9 bảng chuẩn hóa (users, friends, chat_rooms, room_members, chat_messages, file_transfers, voice_sessions, server_logs, app_settings).

4. Vận hành & Kiểm thử
Khởi chạy: Cung cấp sẵn script 1-click run-server.bat (chạy Server) và run-client.bat (chạy Client).

Tài khoản test: Tạo sẵn các user alice, bob, charlie, admin (mật khẩu mặc định: password123 / admin123).

Độ ổn định: Vượt qua 10/10 bài test tự động (--test), có kịch bản test chuyên biệt cho file 20 GiB (--test-20gb) và Voice UDP (--test-voice).