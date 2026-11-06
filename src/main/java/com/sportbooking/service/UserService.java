package com.sportbooking.service;

import com.sportbooking.common.PageResponse;
import com.sportbooking.dto.request.UpdateProfileRequest;
import com.sportbooking.dto.response.UserResponse;
import com.sportbooking.entity.User;
import com.sportbooking.entity.enums.Role;
import com.sportbooking.entity.enums.UserStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.mapper.UserMapper;
import com.sportbooking.repository.RefreshTokenRepository;
import com.sportbooking.repository.UserRepository;
import com.sportbooking.repository.UserSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /** readOnly: báo Hibernate không cần theo dõi thay đổi (dirty checking) -> nhẹ hơn. */
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserMapper.toResponse(findUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.updateProfile(request.fullName().trim(), request.phone());
        // Không cần gọi save(): entity đang được quản lý trong transaction,
        // Hibernate tự phát hiện thay đổi và UPDATE khi commit.
        return UserMapper.toResponse(user);
    }

    // ===== Dành cho ADMIN =====

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchUsers(Role role, UserStatus status, String keyword, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.hasRole(role),
                UserSpecifications.hasStatus(status),
                UserSpecifications.matchesKeyword(keyword));
        return PageResponse.from(userRepository.findAll(spec, pageable).map(UserMapper::toResponse));
    }

    @Transactional
    public UserResponse changeStatus(Long targetUserId, UserStatus newStatus) {
        User user = findUser(targetUserId);
        if (user.isAdmin()) {
            // Chặn admin tự khóa mình hoặc khóa admin khác
            throw new AppException(ErrorCode.CANNOT_MODIFY_ADMIN);
        }
        user.changeStatus(newStatus);
        if (newStatus == UserStatus.LOCKED) {
            // Đăng xuất user khỏi mọi thiết bị: không refresh được nữa
            refreshTokenRepository.revokeAllByUserId(user.getId(), clock.instant());
        }
        return UserMapper.toResponse(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng id=" + userId));
    }
}
