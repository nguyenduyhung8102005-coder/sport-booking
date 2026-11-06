package com.sportbooking.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Format response thống nhất cho mọi API.
 * Thành công: { success: true, data: ..., timestamp }
 * Thất bại:   { success: false, error: { code, message, details }, timestamp }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> fail(String code, String message, List<FieldError> details) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, details), Instant.now());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ErrorBody(String code, String message, List<FieldError> details) {
    }

    public record FieldError(String field, String message) {
    }
}
