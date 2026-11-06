# Sport Booking API

REST API cho hệ thống đặt sân thể thao (bóng đá mini 5/7 người, cầu lông).
Dự án cá nhân phục vụ ứng tuyển vị trí Intern Backend Java.

## Tech stack

Java 21 · Spring Boot 3.5 · Spring Data JPA (Hibernate) · PostgreSQL 17 · Flyway ·
Spring Security + JWT (jjwt) · Swagger/OpenAPI · Docker Compose · JUnit 5 · Mockito · Testcontainers

## Tiến độ

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| 1 | Khởi tạo project, schema Flyway, Swagger, response format, exception handler | ✅ Hoàn thành |
| 2 | Đăng ký / đăng nhập, JWT access + refresh token, phân quyền, duyệt chủ sân | ✅ Hoàn thành |
| 3 | Quản lý cụm sân, sân con, bảng giá; tìm kiếm có phân trang và lọc | 🟡 Xong phần code |
| 4 | Xem slot trống, đặt sân chống double booking, test đồng thời | ⏳ |
| 5 | Giữ chỗ 10 phút, `@Scheduled` tự hủy booking quá hạn | ⏳ |
| 6 | Thanh toán VNPay sandbox, IPN idempotent | ⏳ |
| 7 | Email `@Async`, đánh giá, thống kê doanh thu | ⏳ |
| 8 | Hoàn thiện test, Dockerfile, tài liệu | ⏳ |

## Chạy project

Yêu cầu: JDK 21, Docker, Maven (hoặc IntelliJ IDEA, đã có sẵn Maven).

```bash
# 1. Khởi động PostgreSQL
docker compose up -d

# 2. Chạy ứng dụng (hoặc Run SportBookingApplication trong IntelliJ)
mvn spring-boot:run

# 3. Chạy unit test
mvn test
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Reset sạch database: `docker compose down -v`

### Biến môi trường

| Biến | Mặc định (chỉ dùng khi dev) | Ý nghĩa |
|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | `sport` / `sport123` | Kết nối PostgreSQL |
| `JWT_SECRET` | khóa dev trong `application.yml` | Base64 của khóa ≥ 256 bit. Tạo: `openssl rand -base64 32` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | `admin@sportbooking.vn` / `Admin@123456` | Tài khoản ADMIN tạo tự động khi khởi động lần đầu |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` tự tạo dữ liệu mẫu; đặt `prod` khi deploy |

### Dữ liệu mẫu (profile `dev`)

Lần chạy đầu tiên tự tạo 4 cụm sân ở Hà Nội (1 cụm đang tạm ngừng để kiểm tra bộ lọc) và 2 tài khoản demo,
mật khẩu đều là `Demo@123456`:

| Email | Role |
|---|---|
| `owner@sportbooking.vn` | OWNER (đã được duyệt), sở hữu toàn bộ sân mẫu |
| `customer@sportbooking.vn` | CUSTOMER |

## Xác thực

- **Access token**: JWT ký HMAC-SHA256, sống 15 phút, gửi kèm header `Authorization: Bearer <token>`.
- **Refresh token**: chuỗi ngẫu nhiên 256 bit, sống 7 ngày, DB chỉ lưu hash SHA-256.
  Mỗi refresh token chỉ dùng được 1 lần (rotation). Dùng lại token đã thu hồi → thu hồi toàn bộ phiên của user.
- Server luôn đọc role và trạng thái mới nhất từ DB ở mỗi request:
  khóa tài khoản hay được duyệt làm chủ sân đều có hiệu lực ngay.

| Mã lỗi | HTTP | Client nên làm gì |
|---|---|---|
| `UNAUTHORIZED` | 401 | Chưa gửi token → đăng nhập |
| `TOKEN_EXPIRED` | 401 | Gọi `/auth/refresh` rồi thử lại request |
| `INVALID_TOKEN` | 401 | Token hỏng → đăng nhập lại |
| `INVALID_REFRESH_TOKEN` | 401 | Đăng nhập lại |
| `ACCOUNT_LOCKED` | 403 | Tài khoản bị khóa |
| `FORBIDDEN` | 403 | Không đủ quyền |

## Kiểm tra nhanh (Giai đoạn 2)

```bash
# Đăng ký -> 201, nhận accessToken + refreshToken
curl -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
  -d '{"email":"an@gmail.com","password":"Matkhau123","fullName":"Nguyễn An","phone":"0912345678"}'

# Đăng nhập
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"an@gmail.com","password":"Matkhau123"}'

# Gọi API cần đăng nhập
curl http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <accessToken>"

# Làm mới token (refresh token cũ bị thu hồi ngay)
curl -X POST http://localhost:8080/api/v1/auth/refresh -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

Trên Swagger: gọi `/auth/login`, copy `accessToken`, bấm nút **Authorize** và dán token vào.

## Tìm sân (Giai đoạn 3)

```bash
# Sân bóng 5 người ở Hà Nội, giá ngày thường từ 300k/giờ trở xuống, sắp xếp theo tên
curl "http://localhost:8080/api/v1/venues?city=Hà%20Nội&sportType=FOOTBALL_5&dayType=WEEKDAY&maxPrice=300000&sort=name,asc"

# Chi tiết cụm sân kèm bảng giá
curl http://localhost:8080/api/v1/venues/1
```

| Tham số | Ý nghĩa |
|---|---|
| `keyword` | Tìm trong tên cụm sân |
| `city`, `district` | So khớp không phân biệt hoa thường |
| `sportType` | `FOOTBALL_5`, `FOOTBALL_7`, `BADMINTON` |
| `minPrice`, `maxPrice` | VND/giờ, áp dụng cho CÙNG sân con đúng loại |
| `dayType` | `WEEKDAY` / `WEEKEND`: lọc giá theo loại ngày |
| `page`, `size`, `sort` | Mặc định `page=0&size=10&sort=avgRating,desc`. Sắp xếp được theo `avgRating`, `reviewCount`, `name`, `createdAt` |

Mỗi trang kết quả chỉ tốn 3 câu SQL (danh sách, đếm tổng, thống kê loại sân + giá), không phụ thuộc số cụm sân trong trang.

## Quyết định thiết kế chính

- **Slot cố định 30 phút.** Mỗi lượt đặt được tách thành các slot trong bảng `booking_slots`.
  Ràng buộc `UNIQUE (court_id, slot_date, slot_start)` là lớp chống double booking ở mức database.
- **Thời lượng tối thiểu** theo loại sân, lưu trong enum `SportType`: bóng đá 1 tiếng (2 slot), cầu lông 30 phút (1 slot).
- **Flyway quản lý schema**, Hibernate chỉ `validate`. Migration đã chạy thì không sửa, chỉ thêm phiên bản mới.
- **Tiền lưu bằng `BIGINT` (VND).** Không dùng `double`/`float` cho tiền.
- **Mọi constraint đều được đặt tên** để ánh xạ lỗi database sang mã lỗi nghiệp vụ.
- **Response thống nhất** qua `ApiResponse<T>`; lỗi trong controller đi qua `GlobalExceptionHandler`,
  lỗi 401/403 của Spring Security đi qua `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`.
- **Entity không có setter**; trạng thái chỉ đổi qua method nghiệp vụ (`lock`, `promoteToOwner`...).
- **Inject `Clock`** thay vì gọi `Instant.now()` để test được logic phụ thuộc thời gian.
- **Giá lưu theo giờ**, là bội số 1.000đ (migration V2) → giá 1 slot 30 phút luôn là số nguyên.
- **Chủ sân chỉ thao tác được trên sân của mình**: truy vấn luôn kèm điều kiện `owner_id`;
  sân của người khác trả 404 thay vì 403 để không lộ sự tồn tại.
- **Phân trang an toàn**: giới hạn `size ≤ 100`, chỉ cho sắp xếp theo whitelist, luôn thêm `id` làm tiêu chí phụ
  để thứ tự giữa các trang ổn định.

## Giới hạn đã biết

- Sân không mở cửa qua nửa đêm; giờ đóng cửa muộn nhất là 23:30.
- Tỉnh/quận là text tự do và so khớp có dấu ("Ha Dong" không khớp "Hà Đông").
  Cải tiến: bảng danh mục tỉnh/quận chuẩn hóa, hoặc extension `unaccent` của PostgreSQL.
- Tìm theo tên dùng `LIKE '%...%'`, không dùng được index B-tree; dữ liệu lớn cần index trigram (`pg_trgm`).
- Đăng xuất chỉ thu hồi refresh token; access token vẫn dùng được tới khi hết hạn (tối đa 15 phút).
  Chặn ngay cần blacklist (dự kiến dùng Redis sau MVP).
