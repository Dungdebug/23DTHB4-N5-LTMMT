# Hướng dẫn đóng góp dự án 

## 1. Thiết lập thông tin cá nhân
Trên máy cục bộ, chạy lệnh sau:

```bash
git config --global user.name "MSSV_HoVaTen"
git config --global user.email "email_sinh_vien@domain"
```

Ví dụ:

```bash
git config --global user.name "23110001_NguyenVanA"
git config --global user.email "23110001@student.edu.vn"
```

## 2. Tạo Issue trước khi code
Mỗi task phải bắt đầu bằng Issue.

Yêu cầu:
- Mỗi Issue chỉ assign đúng 1 người phụ trách chính
- Gắn label phù hợp
- Gắn milestone phù hợp

## 3. Tạo nhánh cá nhân
Tên nhánh tuân theo mẫu:

```bash
feature/<MSSV>-issue-<Số_Issue>-<Tên_ngắn_gọn>
```

Ví dụ:

```bash
git checkout main
git pull origin main
git checkout -b feature/23110001-issue-5-login-form
```

## 4. Commit theo chuẩn
Dùng Conventional Commits:

```bash
git commit -m "feat: thêm chức năng đăng nhập"
git commit -m "fix: sửa lỗi crash khi mất kết nối mạng"
git commit -m "refactor: tối ưu code xử lý API"
git commit -m "docs: cập nhật hướng dẫn cài đặt"
```

### Kích hoạt hook commit message (khuyến nghị)

```bash
git config core.hooksPath .githooks
chmod +x .githooks/commit-msg
```

Với hook đã đặt, mọi commit message sai định dạng sẽ bị từ chối.

## 5. Merge Request / Pull Request
Khi task hoàn tất:
- push nhánh cá nhân
- mở Pull Request vào `main`
- mô tả rõ: đã làm gì, module nào bị ảnh hưởng, kết quả kiểm tra
- có ít nhất 1 reviewer khác approve
- không được tự approve cho mình

## 6. Kiểm tra và review
- Không push trực tiếp lên `main`
- Không bỏ qua review
- Không commit thay cho thành viên khác
- Không dùng tài khoản hoặc máy của người khác để commit hộ

## 7. Bảng đóng góp
Bảng thống kê đóng góp phải được cập nhật trong `README.md`.

## 8. Quy định về file thừa
Không commit các file sau:
- `node_modules/`
- `target/`, `bin/`, `build/`
- `.idea/`, `.vscode/`
- file nén lớn như `.jar`, `.zip`
- file nhị phân và log không cần thiết

## 9. Các bước bắt đầu làm việc

```bash
git checkout main
git pull origin main
git checkout -b feature/<MSSV>-issue-<SỐ_ISSUE>-<TÊN>
```

Sau khi hoàn thành task:

```bash
git add .
git commit -m "feat: ..."
git push origin feature/<MSSV>-issue-<SỐ_ISSUE>-<TÊN>
```

Mở Pull Request trên GitHub để yêu cầu merge. 
