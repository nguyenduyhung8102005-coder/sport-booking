package com.sportbooking.exception;

import lombok.Getter;

/**
 * Exception nghiệp vụ duy nhất của ứng dụng.
 * Kế thừa RuntimeException vì @Transactional mặc định chỉ rollback với unchecked exception.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
