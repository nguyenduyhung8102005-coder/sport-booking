package com.sportbooking.common;

import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

public final class PageableUtils {

    private PageableUtils() {
    }

    /**
     * 1. Chỉ cho sắp xếp theo các field trong danh sách cho phép (whitelist):
     *    client không thể ?sort=passwordHash hay sort theo cột không có index làm chậm DB.
     *
     * 2. Luôn thêm "id" làm tiêu chí phụ (tie-breaker). Nếu 50 sân cùng avgRating = 0,
     *    PostgreSQL không cam kết thứ tự giữa chúng -> trang 1 và trang 2 có thể trùng
     *    hoặc sót sân. Thêm id thì thứ tự luôn xác định.
     */
    public static Pageable withAllowedSort(Pageable pageable, List<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Không hỗ trợ sắp xếp theo '" + order.getProperty() + "'. Cho phép: " + allowedProperties);
            }
        }
        Sort stableSort = pageable.getSort().and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), stableSort);
    }
}
