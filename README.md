## 1. Thông Tin Nhóm

**Tên Dự Án:** O-Range TimetableAI

**Link Dự Án:** https://github.com/CheinP9/O-Range-TimetableAI.git

## 2. Giới Thiệu Dự Án

**Mô tả:** O-Range là ứng dụng quản lý thời khóa biểu thông minh. Điểm khác biệt cốt lõi là việc ứng dụng AI (OpenRouter) để tự động hóa lộ trình học tập và hệ thống nhắc nhở đa nền tảng qua Email, giúp người dùng không bỏ lỡ các mục tiêu cá nhân.

## 3. Các Chức Năng Chính

- [**Nhóm chức năng Hệ thống & Bảo mật**]:
  - **Quản lý người dùng**: Đăng ký tài khoản mới, đăng nhập và xác thực, đổi mật khẩu.
- [**Nhóm chức năng Lập kế hoạch với AI**]
  - **Tạo lộ trình thông minh**: Sử dụng OpenRouterAI để phân tích chủ đề người dùng muốn học và tự động sinh ra các LearningPlan.
  - **Phân bổ lịch trình**: Tự động chia nhỏ lộ trình thành các ScheduleSlot dựa trên đề xuất của AI.
- [**Nhóm chức năng Thời khóa biểu**]
  - **Hiển thị lịch trình**: Hiển thị danh sách các buổi học trực quan.
  - **Quản lý trạng thái**: Cập nhật trạng thái của từng slot học (Hoàn thành/Chưa hoàn thành)
- [**Nhóm chức năng Nhắc nhở**]
  - **Nhắc lịch tự động**: Gửi dữ liệu từ app chính sang mailservice.
  - **Gửi Email**: Thực hiện gửi email nhắc nhở người dùng học tập đúng giờ.
- [**Nhóm chức năng Tiện ích**]
  -  Export lịch trình ra file .ics để người dùng dễ dàng import vào Google Calendar.


## 4. Công nghệ

### 4.1. Công Nghệ Sử Dụng  
- Ngôn ngữ: Java (JDK 21)
- Giao diện: JavaFX (FXML & CSS)
- Database: Hibernate / JPA kết hợp PostgreSQL
- Mailing: Spring Boot Email Starter
- AI: OpenRouter API
- Scheduling: JobRunr


### 4.2. Kiến trúc hệ thống

Hệ thống bao gồm hai thành phần độc lập giao tiếp với nhau thông qua giao thức HTTP và định dạng dữ liệu JSON, đảm bảo tính độc lập và khả năng mở rộng (Scalability).

- O-Range Main App (Java): Xử lý giao diện và nghiệp vụ chính.

- Mail Service (Spring Boot): Một dịch vụ riêng biệt chuyên trách việc gửi email, giúp tách biệt hoàn toàn logic thông báo khỏi ứng dụng chính.

### 4.3. Cấu trúc phân lớp và Mô hình triển khai

Dự án O-Range App được thiết kế theo Kiến trúc phân lớp (Layered Architecture) kết hợp với mô hình MVC (Model-View-Controller), nhằm đảm bảo tính rời rạc, dễ bảo trì và dễ dàng mở rộng trong tương lai. Hệ thống được chia thành 3 lớp chính:

1. **Presentation Layer (Tầng giao diện - MVC):**

   - View (FXML & CSS): Chịu trách nhiệm hiển thị giao diện người dùng (UI) như màn hình đăng nhập, thời khóa biểu, biểu đồ tiến độ.
   - Controller: Tiếp nhận các sự kiện từ người dùng (click, nhập liệu), gọi đến tầng Service để xử lý nghiệp vụ và cập nhật lại kết quả lên View. (Ví dụ: LoginController, TimetableController...).
   
2. **Business Logic Layer (Tầng nghiệp vụ - Service):**

- Đây là trái tim của ứng dụng, chứa toàn bộ logic xử lý phức tạp. Tầng này hoàn toàn độc lập với giao diện, giúp code có thể tái sử dụng.

- Xử lý việc giao tiếp với OpenRouter API để sinh lộ trình học, chia nhỏ các ScheduleSlot... (Ví dụ: LearningPlanService).

3. **Data Access Layer (Tầng truy cập dữ liệu - Repository & Entity):**

- Sử dụng Repository Pattern để trừu tượng hóa các thao tác tương tác với cơ sở dữ liệu PostgreSQL thông qua Hibernate/JPA.

- Tầng này cung cấp các hàm (CRUD) để Service gọi xuống mà không cần quan tâm đến câu lệnh SQL bên dưới. Việc này tuân thủ chặt chẽ tính Đóng gói (Encapsulation) trong OOP.

**Áp dụng Design Patterns:**
Bên cạnh kiến trúc tổng thể, dự án còn áp dụng một số Design Pattern cốt lõi:

- Singleton Pattern: Áp dụng cho SessionManager để lưu trữ thông tin User đang đăng nhập duy nhất trong suốt vòng đời ứng dụng; và HibernateUtil để quản lý Connection Factory, tránh rò rỉ bộ nhớ.

- Data Transfer Object (DTO): Sử dụng các class DTO ở request/response để bọc dữ liệu, tránh việc lộ cấu trúc Entity thật ra ngoài View hoặc khi giao tiếp với API bên ngoài.

### 4.4 Cấu trúc dự án

Client Application
```
com.ocr.javafx
├── config/             # Cấu hình hệ thống 
├── controller/         # Controller (Base, Login, Timetable...)
├── dto/                # Data Transfer Objects (Request/Response)
│   ├── request/        # DTO cho dữ liệu gửi đi (Auth, Reminder...)
│   └── response/       # DTO nhận về từ API/Service
├── entity/             # Mapping Database (User, LearningPlan, ScheduledSlot...)
├── enums/              # Định nghĩa các hằng số (Status, Role)
├── repository/         # Tầng truy vấn dữ liệu (Hibernate/JPA)
├── service/            # Tầng nghiệp vụ (AuthService, LearningPlanService, OpenRouterAI...)
├── util/               # Helper classes (DateFormatter, SessionManager)
└── MainApplication     # Điểm khởi chạy JavaFX
resources/
├── views/              # Chứa các file giao diện (.fxml)
...
├── style/              # Chứa các file định dạng giao diện (.css)
├── image/              # Chứa icon, logo, hình ảnh minh họa
├── db/                 # Scripts khởi tạo Database
├── application.properties # Cấu hình môi trường
└── hibernate.cfg.xml   # Cấu hình Hibernate ORM
```

Mail Service

```
com.ocr.mailservice
├── controller/ # Tiếp nhận yêu cầu gửi mail (ReminderController)
├── dto/ # Định dạng dữ liệu yêu cầu (ReminderRequest)
├── service/ # Logic gửi mail thực tế (EmailService)
└── MailServiceApplication # Điểm khởi chạy dịch vụ Mail
```

### 4.5. Hạ tầng và Triển khai (Infrastructure & Deployment)

Hệ thống được triển khai theo mô hình phân tán, tách biệt giữa Client và Background Services để tối ưu hiệu năng và đảm bảo thông báo luôn được gửi đúng giờ ngay cả khi người dùng không mở ứng dụng.

- **Cloud Database (PostgreSQL):** Sử dụng **Neon Tech** (Serverless Postgres) làm cơ sở dữ liệu lưu trữ đám mây chính. Neon cung cấp khả năng tự động giãn nở (autoscaling) và đồng bộ dữ liệu thời gian thực giữa JavaFX Client và Mail Service. Hệ thống cũng tự động khởi tạo các bảng `jobrunr_*` để quản lý hàng đợi gửi mail.
- **Mail Service (Spring Boot):** Triển khai trực tiếp lên **Render** (Platform-as-a-Service) thông qua quy trình CI/CD tự động từ GitHub. Các thông tin nhạy cảm (Credentials, Database URL, Mail Password) được bảo mật hoàn toàn qua cơ chế Environment Variables của Render.
- **Dịch vụ SMTP (Gửi Email):** Tích hợp dịch vụ **Brevo (Sendinblue)** và **Gmail SMTP** qua cổng bảo mật SSL (Port 465/2525) để thực hiện gửi mail định dạng HTML, tránh tình trạng bị các bộ lọc đưa vào hòm Thư rác (Spam).
- **Client Application (JavaFX):** Đóng gói thành file thực thi `.jar`, giao tiếp với Server thông qua RESTful API kết hợp Header bảo mật định danh (`X-Internal-Key`) để cấp quyền gọi lệnh đặt/hủy lịch nhắc nhở.
