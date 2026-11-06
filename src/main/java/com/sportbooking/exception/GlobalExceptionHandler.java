package com.sportbooking.exception;

import com.sportbooking.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Bắt mọi exception ném ra từ controller trở vào trong và chuyển thành ApiResponse lỗi.
 * Lưu ý: KHÔNG bắt được exception ném trong Filter (ví dụ JWT filter ở Giai đoạn 2).
 *
 * Lỗi 401/403 do Spring Security chặn TRƯỚC khi vào controller được xử lý ở
 * RestAuthenticationEntryPoint / RestAccessDeniedHandler, không phải ở đây.
 *
 * Sẽ bổ sung:
 *  - GĐ 4: DataIntegrityViolationException (trùng slot -> SLOT_ALREADY_BOOKED)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        return build(ex.getErrorCode(), ex.getMessage(), null);
    }

    /**
     * BindException là lớp cha của MethodArgumentNotValidException, nên bắt được cả 2 nguồn lỗi:
     *  - @Valid @RequestBody (JSON body)
     *  - @Valid với object gom từ query param (VenueSearchRequest), kể cả lỗi chuyển kiểu
     *    như ?sportType=TENNIS hay ?minPrice=abc (isBindingFailure = true).
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(BindException ex) {
        List<ApiResponse.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.FieldError(fe.getField(),
                        // Lỗi chuyển kiểu có message kỹ thuật rất dài -> thay bằng câu dễ hiểu
                        fe.isBindingFailure() ? "Giá trị '" + fe.getRejectedValue() + "' không hợp lệ" : fe.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), details);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,       // JSON sai cú pháp, sai kiểu dữ liệu
            MethodArgumentTypeMismatchException.class,   // /bookings/abc thay vì /bookings/1
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.debug("Bad request: {}", ex.getMessage());
        return build(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.getDefaultMessage(), null);
    }

    /** ?sort=abc với "abc" không phải field của entity. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadSort(PropertyReferenceException ex) {
        return build(ErrorCode.BAD_REQUEST, "Không thể sắp xếp theo trường '" + ex.getPropertyName() + "'", null);
    }

    /**
     * Phòng trường hợp kiểm tra quyền ở tầng method (@PreAuthorize) sau này:
     * exception đó ném ra BÊN TRONG controller nên đi vào đây chứ không qua AccessDeniedHandler.
     * Thiếu handler này, catch-all bên dưới sẽ biến nó thành lỗi 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoEndpoint(NoResourceFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, "Endpoint không tồn tại", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage(), null);
    }

    /** Lưới an toàn cuối: log chi tiết cho dev, client chỉ nhận câu chung chung. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), null);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message,
                                                    List<ApiResponse.FieldError> details) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.name(), message, details));
    }
}
