package com.sportbooking.mapper;

import com.sportbooking.dto.response.OwnerApplicationResponse;
import com.sportbooking.dto.response.UserResponse;
import com.sportbooking.entity.OwnerProfile;
import com.sportbooking.entity.User;

/**
 * Map tay entity -> DTO. Cách khác: MapStruct sinh code tự động lúc compile,
 * gọn hơn khi có nhiều DTO, đổi lại phải học thêm cú pháp và cấu hình annotation processor.
 */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt());
    }

    /** Phải gọi trong transaction (hoặc profile đã được load kèm user) vì user là LAZY. */
    public static OwnerApplicationResponse toResponse(OwnerProfile profile) {
        User user = profile.getUser();
        return new OwnerApplicationResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                profile.getBusinessName(),
                profile.getApprovalStatus(),
                profile.getCreatedAt(),
                profile.getReviewedAt(),
                profile.getRejectReason());
    }
}
