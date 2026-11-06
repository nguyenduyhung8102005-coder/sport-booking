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
| 3 | Quản lý cụm sân, sân con, bảng giá; tìm kiếm có phân trang và lọc | ✅ Hoàn thành |
| 4 | Xem slot trống, đặt sân chống double booking, test đồng thời | ✅ Hoàn thành |
| 5 | Giữ chỗ 10 phút, `@Scheduled` tự hủy booking quá hạn, hủy theo chính sách, lịch chủ sân | ✅ Hoàn thành |
| 6 | Thanh toán VNPay sandbox, IPN idempotent | 🟡 Xong phần code |
| 7 | Email `@Async`, đánh giá, thống kê doanh thu | ⏳ |
| 8 | Hoàn thiện test, Dockerfile, tài liệu | ⏳ |

## Chạy project

Yêu cầu: JDK 21, Docker, Maven (hoặc IntelliJ IDEA, đã có sẵn Maven).

```bash
# 1. Khởi động PostgreSQL
docker compose up -d

# 2. Chạy ứng dụng (hoặc Run SportBookingApplication trong IntelliJ)
mvn spring-boot:run

# 3. Chạy toàn bộ test (unit + integration). Integration test cần Docker đang chạy:
#    Testcontainers tự khởi động 1 PostgreSQL 17 riêng cho test.
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
| `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` | (trống) | Thông tin sandbox VNPay. Đặt trong file `.env` (xem `.env.example`, không commit) |
| `VNPAY_RETURN_URL` | `http://localhost:8080/api/v1/payments/vnpay/return` | Nơi VNPay chuyển trình duyệt về |

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

## Đặt sân (Giai đoạn 4)

```bash
# Lịch trống 1 ngày của sân, theo slot 30 phút (public)
curl "http://localhost:8080/api/v1/courts/1/availability?date=2026-11-09"

# Đặt sân (cần đăng nhập) -> 201, trạng thái PENDING, giữ chỗ 10 phút chờ thanh toán
curl -X POST http://localhost:8080/api/v1/bookings -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"courtId":1,"date":"2026-11-09","startTime":"17:00","endTime":"18:30"}'

# Tìm cụm sân còn trống sân bóng 5 người lúc 17:00–18:30
curl "http://localhost:8080/api/v1/venues?sportType=FOOTBALL_5&date=2026-11-09&startTime=17:00&endTime=18:30"
```

Quy tắc: giờ tròn `:00`/`:30`, trong giờ mở cửa, không đặt giờ đã qua, đặt trước tối đa 30 ngày,
bóng đá tối thiểu 1 tiếng / cầu lông 30 phút, tối đa 4 tiếng, tối đa 3 lượt chưa thanh toán cùng lúc.
Giá tính theo từng slot nên lượt đặt vắt qua 2 khung giá được tính đúng. Giá do server tính, không nhận từ client.

## ⭐ Chống đặt trùng (double booking)

**Vấn đề.** "Kiểm tra còn trống rồi mới lưu" (check-then-act) là SAI khi có nhiều request cùng lúc:
cả hai cùng đọc thấy "trống" trước khi ai kịp lưu → cả hai cùng đặt thành công.

```
Thời gian   Request A                     Request B
   t1       SELECT: 17:00 còn trống
   t2                                     SELECT: 17:00 còn trống
   t3       INSERT booking 17:00
   t4                                     INSERT booking 17:00   ← trùng!
```

**Giải pháp đã chọn: UNIQUE constraint.** Mỗi lượt đặt được tách thành các slot 30 phút lưu vào
`booking_slots` với `UNIQUE (court_id, slot_date, slot_start)`, trong cùng transaction với booking.
Request đến sau INSERT trùng slot sẽ bị PostgreSQL chặn (`duplicate key`) → rollback → trả `409 SLOT_ALREADY_BOOKED`.

| Cách | Cơ chế | Ưu | Nhược |
|---|---|---|---|
| **Unique constraint** ✅ | DB từ chối dòng trùng | Database đảm bảo, đúng cả khi chạy nhiều instance; các slot khác nhau chạy song song; ít code | Cần mô hình slot rời rạc; phát hiện qua exception |
| Pessimistic lock | `SELECT ... FOR UPDATE` dòng của sân, rồi kiểm tra và ghi | Dùng được cho khoảng giờ tự do | Xếp hàng **mọi** request của sân, kể cả không tranh nhau; giữ khóa lâu; nguy cơ deadlock; phải nhớ khóa ở mọi chỗ ghi |
| Optimistic lock | Cột `version`, `UPDATE ... WHERE version = ?` | Không khóa khi đọc | Request thua đã làm hết việc rồi mới bị hủy; nhiều tranh chấp → phải retry nhiều; không tự nhiên với INSERT (phải "mượn" version của sân) |

Kết quả thí nghiệm trên PostgreSQL thật (`LockingStrategiesIntegrationTest`, 50ms xử lý giữa kiểm tra và ghi):

| | 50 request cùng đặt 17:00 | 20 request đặt 20 slot khác nhau |
|---|---|---|
| check-then-act | **nhiều dòng trùng** ❌ | |
| Unique constraint | 1 dòng ✅ | ~0,35 giây |
| Pessimistic lock | 1 dòng ✅ | ~1,2 giây (xếp hàng từng request) |
| Optimistic lock | 1 dòng ✅ | |

Kiểm chứng trên code thật (`BookingConcurrencyIntegrationTest`): 50 thread gọi `BookingService` cùng lúc
→ đúng 1 thành công, 49 nhận `SLOT_ALREADY_BOOKED`, database có đúng 1 booking và 2 slot.
Hai khoảng giao nhau (17:00–18:00 và 17:30–18:30) cũng chỉ 1 thắng; hai khoảng liền kề (17–18h, 18–19h) đều thành công.
Slot luôn được INSERT theo thứ tự giờ tăng dần nên không xảy ra deadlock.

## Vòng đời lượt đặt (Giai đoạn 5)

```
PENDING ──(thanh toán)──> CONFIRMED ──(chơi xong)──> COMPLETED
   │                          │
   ├──(quá 10 phút)──> EXPIRED │
   └──(khách / chủ sân hủy)────┴──> CANCELLED
```

- **Tự hết hạn:** `BookingExpirationJob` (`@Scheduled`, `fixedDelay` 30 giây) chuyển PENDING quá hạn sang EXPIRED và nhả slot.
  Mỗi booking xử lý trong 1 transaction riêng; query dùng partial index `idx_bookings_pending_expiry`.
- **Hủy:** khách hủy PENDING lúc nào cũng được (trước giờ chơi); lượt đã thanh toán chỉ hủy được trước giờ chơi ≥ 24 giờ.
  Chủ sân hủy được lượt tại sân của mình, bắt buộc ghi lý do. Slot được nhả ngay.
- **Cuộc đua job ↔ khách hủy (↔ thanh toán ở GĐ 6):** `@Version` (optimistic lock) đảm bảo chỉ một bên thắng.
  Mọi luồng đều UPDATE `bookings` trước rồi mới xóa `booking_slots` → cùng thứ tự khóa → không deadlock.
  `BookingExpirationIntegrationTest` cho job và lệnh hủy chạy song song 10 lần: lần nào cũng đúng 1 bên thắng.

```bash
# Khách hủy (body không bắt buộc)
curl -X POST http://localhost:8080/api/v1/bookings/5/cancel -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"reason":"Bận việc đột xuất"}'

# Chủ sân xem lịch 1 ngày của cụm sân, hủy lượt đặt
curl "http://localhost:8080/api/v1/owner/venues/1/bookings?date=2026-11-09" -H "Authorization: Bearer <ownerToken>"
curl -X POST http://localhost:8080/api/v1/owner/bookings/5/cancel -H "Authorization: Bearer <ownerToken>" \
  -H "Content-Type: application/json" -d '{"reason":"Sân bảo trì"}'
```

## ⭐ Thanh toán VNPay (Giai đoạn 6)

```
Khách ──POST /bookings/{id}/payments──> Server: lưu Payment PENDING, trả paymentUrl đã ký
Khách ──mở paymentUrl──> trang VNPay ──thanh toán──┬──> IPN (server VNPay gọi server mình)   ← GHI NHẬN TIỀN
                                                    └──> Return URL (trình duyệt quay về)    ← CHỈ HIỂN THỊ
```

| Nguyên tắc | Cách làm |
|---|---|
| Không tin dữ liệu chưa ký | Mọi tham số từ VNPay được kiểm tra chữ ký HMAC-SHA512 trước khi đọc DB; so sánh chữ ký bằng `MessageDigest.isEqual` |
| IPN là nguồn sự thật duy nhất | Return URL đi qua trình duyệt, khách có thể đóng tab: chỉ hiển thị, không đổi trạng thái |
| Idempotent | IPN gửi lại (kể cả đồng thời) chỉ xử lý 1 lần: khóa dòng payment `FOR UPDATE`, đã xử lý thì trả `02` |
| Đối chiếu số tiền | `vnp_Amount / 100` phải bằng số tiền đã lưu, lệch thì trả `04` |
| Hạn thanh toán = hạn giữ chỗ | `vnp_ExpireDate` = `expires_at` của lượt đặt |
| Tiền về khi lượt đặt đã hết hạn | Không "hồi sinh" lượt đặt (slot có thể đã thuộc người khác): payment `REFUND_REQUIRED` |
| Tranh chấp với job hết hạn | `@Version` trên booking; IPN thua trả `99` → VNPay tự gửi lại → lần sau xử lý theo trạng thái mới |

Mã trả về cho VNPay: `00` xử lý xong · `01` không thấy giao dịch · `02` đã xử lý · `04` sai số tiền · `97` sai chữ ký · `99` lỗi khác.

### Chạy thử với sandbox

1. Đăng ký tại https://sandbox.vnpayment.vn/devreg/ để nhận `vnp_TmnCode` và `vnp_HashSecret` qua email.
2. `cp .env.example .env`, điền 2 giá trị trên, khởi động lại app.
3. Đăng nhập customer, đặt sân, gọi `POST /api/v1/bookings/{id}/payments`, mở `paymentUrl` trên trình duyệt,
   thanh toán bằng thẻ test của sandbox (ngân hàng NCB, thông tin thẻ có trong tài liệu / email sandbox).
4. **IPN khi chạy trên máy cá nhân:** VNPay không gọi được `localhost`. Hai cách:
   - Mở tunnel HTTPS (`ngrok http 8080` hoặc `cloudflared tunnel --url http://localhost:8080`) và cấu hình IPN URL
     `https://<tunnel>/api/v1/payments/vnpay/ipn` trong trang quản trị merchant sandbox.
   - Cách nhanh khi học: sau khi thanh toán, trình duyệt quay về return URL kèm đầy đủ tham số `vnp_*` đã được VNPay ký,
     đúng bộ tham số của IPN. Sao chép phần query đó và gọi `GET /api/v1/payments/vnpay/ipn?<query>` để mô phỏng IPN.

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
- Lọc "sân trống" kiểm tra slot chưa bị chiếm nhưng chưa kiểm tra slot đã có giá; khi đặt thì có kiểm tra.
- Giới hạn 3 lượt chưa thanh toán là giới hạn "mềm": nhiều request song song của cùng 1 khách có thể vượt nhẹ.
- Lượt PENDING quá hạn vẫn chiếm slot thêm tối đa ~30 giây cho tới lần chạy kế tiếp của job.
- Chạy nhiều instance: job vẫn đúng nhờ `@Version` nhưng các instance có thể làm trùng việc; cải tiến bằng ShedLock / khóa Redis.
- Lượt đã thanh toán bị hủy, hoặc payment `REFUND_REQUIRED`: hoàn tiền xử lý thủ công (tự động qua VNPay refund API là phần mở rộng).
- Chưa có job đối soát chủ động (gọi API querydr của VNPay) cho giao dịch PENDING lâu không nhận được IPN.
- Đăng xuất chỉ thu hồi refresh token; access token vẫn dùng được tới khi hết hạn (tối đa 15 phút).
  Chặn ngay cần blacklist (dự kiến dùng Redis sau MVP).
