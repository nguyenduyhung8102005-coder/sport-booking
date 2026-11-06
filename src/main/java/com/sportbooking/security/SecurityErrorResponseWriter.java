package com.sportbooking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportbooking.common.ApiResponse;
import com.sportbooking.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lỗi xảy ra trong Spring Security chain nằm NGOÀI tầm của @RestControllerAdvice,
 * nên phải tự ghi JSON vào response, cùng format ApiResponse với các lỗi khác.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.fail(code.name(), code.getDefaultMessage(), null));
    }
}
