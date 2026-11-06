package com.sportbooking.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Format phân trang ổn định cho client.
 * Không trả thẳng Page của Spring: JSON của PageImpl chứa nhiều field nội bộ
 * và Spring Data không cam kết giữ nguyên cấu trúc đó giữa các phiên bản.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
