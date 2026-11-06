package com.sportbooking.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Danh mục mã lỗi. Frontend dựa vào tên mã (code), không dựa vào message.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== Chung (GĐ 1) =====
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Request không đúng định dạng"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống, vui lòng thử lại sau"),

    // ===== Xác thực & phân quyền (GĐ 2) =====
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để thực hiện thao tác này"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Access token không hợp lệ"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token đã hết hạn"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa"),

    // ===== Người dùng & chủ sân (GĐ 2) =====
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    OWNER_APPLICATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Chỉ khách hàng mới có thể đăng ký làm chủ sân"),
    OWNER_APPLICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bạn đã gửi yêu cầu hoặc đã là chủ sân"),
    OWNER_APPLICATION_NOT_PENDING(HttpStatus.CONFLICT, "Yêu cầu không còn ở trạng thái chờ duyệt"),
    CANNOT_MODIFY_ADMIN(HttpStatus.FORBIDDEN, "Không thể thay đổi trạng thái tài khoản quản trị"),

    // ===== Cụm sân, sân con, bảng giá (GĐ 3) =====
    INVALID_OPENING_HOURS(HttpStatus.BAD_REQUEST, "Giờ đóng cửa phải sau giờ mở cửa"),
    INVALID_PRICE_RANGE(HttpStatus.BAD_REQUEST, "Giá tối thiểu không được lớn hơn giá tối đa"),
    COURT_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tên sân đã tồn tại trong cụm sân này"),
    INVALID_PRICE_RULES(HttpStatus.BAD_REQUEST, "Bảng giá không hợp lệ"),

    // ===== Đặt sân (GĐ 4) =====
    COURT_NOT_AVAILABLE(HttpStatus.CONFLICT, "Sân đang bảo trì hoặc ngừng hoạt động"),
    INVALID_BOOKING_TIME(HttpStatus.BAD_REQUEST, "Thời gian đặt sân không hợp lệ"),
    SLOT_NOT_PRICED(HttpStatus.BAD_REQUEST, "Khung giờ chưa có giá, chưa thể đặt"),
    SLOT_ALREADY_BOOKED(HttpStatus.CONFLICT, "Khung giờ này đã có người đặt, vui lòng chọn giờ khác"),
    TOO_MANY_PENDING_BOOKINGS(HttpStatus.CONFLICT, "Bạn đang có quá nhiều lượt đặt chưa thanh toán"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu vừa bị thay đổi hoặc bị trùng, vui lòng thử lại"),

    // ===== Vòng đời lượt đặt (GĐ 5) =====
    INVALID_BOOKING_STATE(HttpStatus.CONFLICT, "Trạng thái lượt đặt không cho phép thao tác này"),
    CANCELLATION_NOT_ALLOWED(HttpStatus.CONFLICT, "Không thể hủy lượt đặt này"),

    // ===== Thanh toán (GĐ 6) =====
    PAYMENT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Cổng thanh toán VNPay chưa được cấu hình"),
    INVALID_PAYMENT_SIGNATURE(HttpStatus.BAD_REQUEST, "Chữ ký dữ liệu thanh toán không hợp lệ");

    private final HttpStatus status;
    private final String defaultMessage;
}
