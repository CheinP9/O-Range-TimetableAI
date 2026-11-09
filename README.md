# 🍊 O-Range TimetableAI

Ứng dụng lập kế hoạch học tập cá nhân hóa & Thời khóa biểu thông minh tích hợp trí tuệ nhân tạo (AI), hỗ trợ gửi email nhắc nhở học tập và xác thực OTP.

---

## 🌟 Tính Năng Nổi Bật

- 📅 **Thời khóa biểu thông minh (Timetable):**
  - Hiển thị trực quan theo lưới tuần (Monday -> Sunday).
  - Tự động định vị chính xác vị trí và độ dài ô lịch theo khung giờ (`HH:mm`).
  - Hỗ trợ thêm sự kiện cố định, lịch lặp hàng tuần (Recurring Event) có ngày kết thúc bắt buộc.
  - Phân loại màu sắc riêng biệt cho từng Kế hoạch học tập (Learning Plan) giúp dễ nhận biết.
  - Lọc nhanh theo tuần, xuất file lịch `.ics` tương thích Google Calendar / Apple Calendar.
- 🤖 **Sinh lịch học bằng AI (AI Scheduling):**
  - Tự động phân bổ lịch học thông minh vào các khung giờ trống trong tuần dựa trên mục tiêu, cường độ và kế hoạch của người dùng.
- 📚 **Quản lý kế hoạch học tập (Learning Plans):**
  - Đặt mục tiêu học tập theo số buổi (`Target Sessions`), số ngày (`Duration`) và cường độ (`Intensity`).
  - Cho phép xem chi tiết, chỉnh sửa mục tiêu trực tiếp và tự động đồng bộ tiến độ hoàn thành (`%`).
- 📧 **Dịch vụ Email nhắc nhở & Bảo mật (Mail Server):**
  - Tích hợp Spring Boot Mail Service chạy nền.
  - Tự động quét và gửi email nhắc nhở trước giờ học 15 phút.
  - Xác thực tài khoản & Quên mật khẩu qua mã OTP gửi về Email.

---

## 🛠️ Công Nghệ Sử Dụng

- **Frontend / Desktop UI:** JavaFX 21+, FXML, CSS3 phong cách hiện đại.
- **Backend Service:** Spring Boot 3, Java Mail Sender, REST API.
- **Database:** PostgreSQL, Hibernate ORM 6.
- **AI Integration:** OpenRouter API (DeepSeek / Gemini).
- **Build Tool:** Maven Wrapper (`mvnw`).

---

## 🚀 Hướng Dẫn Cài Đặt & Chạy Mã Nguồn Cho Developers

### 1. Yêu cầu môi trường
- **Java Development Kit (JDK):** Phiên bản 21 trở lên.
- **PostgreSQL:** Phiên bản 14 trở lên.

### 2. Cài đặt Cơ sở dữ liệu
1. Mở PostgreSQL (pgAdmin hoặc psql) và tạo Database mới:
   ```sql
   CREATE DATABASE orange_db;
   ```
2. Thực thi toàn bộ script tạo bảng trong file:
   [`src/main/resources/db/schema.sql`](src/main/resources/db/schema.sql)

### 3. Cấu hình Biến môi trường
Tạo file `.env` tại thư mục gốc của dự án (tham khảo file `.env.example`):
```properties
DB_URL=jdbc:postgresql://localhost:5432/orange_db
DB_USER=postgres
DB_PASSWORD=your_password
OPENROUTER_API_KEY=your_openrouter_api_key
```

### 4. Khởi chạy Ứng dụng

#### Bước 1: Khởi động Mail Server (Spring Boot)
Mở một cửa sổ Terminal:
```bash
cd mail-server
mvn spring-boot:run
```
*(Server chạy tại cổng `http://localhost:8080`)*

#### Bước 2: Khởi động Ứng dụng O-Range (JavaFX)
Mở một cửa sổ Terminal khác tại thư mục gốc:
```bash
mvn javafx:run
```

---

## 📦 Hướng Dẫn Sử Dụng Bản Đóng Gói (Cho Người Dùng Cuối)

Nếu bạn không muốn cài đặt công cụ lập trình:
1. Vào mục [**Releases**](../../releases) của Repository này và tải về file `O-Range-App.zip`.
2. Giải nén thư mục `O-Range-App`.
3. Đảm bảo máy tính đã cài đặt Java 21+ và PostgreSQL (đã import file `schema.sql`).
4. Chỉnh sửa mật khẩu Database của bạn trong file `.env` nếu khác mặc định.
5. Nhấp đúp chuột vào file **`run-app.bat`** để khởi chạy ứng dụng tự động!
