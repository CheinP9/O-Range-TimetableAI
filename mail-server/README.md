# O-Range Mail Server (Spring Boot)

Microservice phụ trách gửi Email OTP và Lập lịch gửi lời nhắc học tập (Reminders) cho ứng dụng **O-Range Timetable AI**.

---

## 1. Hướng dẫn lấy Gmail App Password (16 ký tự)

Để hệ thống gửi được email bằng tài khoản Gmail của bạn, Google yêu cầu tạo **Mật khẩu ứng dụng (App Password)**:

1. Đăng nhập vào tài khoản Google của bạn tại: [https://myaccount.google.com/](https://myaccount.google.com/)
2. Vào mục **Bảo mật (Security)**.
3. Bật **Xác minh 2 bước (2-Step Verification)** (nếu chưa bật).
4. Tìm kiếm từ khóa **"Mật khẩu ứng dụng"** (hoặc truy cập trực tiếp: [https://myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)).
5. Đặt tên ứng dụng (ví dụ: `O-Range Mail Server`), bấm **Tạo (Create)**.
6. Google sẽ hiển thị một mật khẩu gồm **16 ký tự** (dạng `xxxx xxxx xxxx xxxx`). Copy mật khẩu này.

---

## 2. Cấu hình Server

Mở file `src/main/resources/application.properties` và điền thông tin:

```properties
spring.mail.username=dia_chi_email_cua_ban@gmail.com
spring.mail.password=xxxx xxxx xxxx xxxx
```

---

## 3. Khởi chạy Server

Mở terminal tại thư mục `mail-server` và chạy lệnh:

```bash
# Trên Windows PowerShell / Command Prompt:
.\mvnw.cmd spring-boot:run
```
Hoặc chỉ cần nhấp đúp chuột vào file **`run.bat`**.

Server sẽ khởi động tại cổng **8080**.

---

## 4. Danh sách các API

| Method | Endpoint | Headers | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/mail/send-otp` | `X-Internal-Key: SieuCapVipPro_2026`<br>`Content-Type: application/json` | Gửi mã OTP xác thực khôi phục mật khẩu |
| `POST` | `/api/reminders/schedule` | `X-Internal-Key: SieuCapVipPro_2026`<br>`Content-Type: application/json` | Lên lịch hẹn gửi email nhắc nhở học tập |
| `DELETE` | `/api/reminders/schedule/{slotId}` | `X-Internal-Key: SieuCapVipPro_2026` | Hủy lịch hẹn nhắc nhở của môn học |
| `GET` | `/api/reminders/status` | `X-Internal-Key: SieuCapVipPro_2026` | Kiểm tra trạng thái và số lượng job đang chờ |
