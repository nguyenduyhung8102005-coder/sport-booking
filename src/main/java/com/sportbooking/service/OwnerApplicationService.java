package com.sportbooking.service;

import com.sportbooking.common.PageResponse;
import com.sportbooking.dto.request.OwnerApplicationRequest;
import com.sportbooking.dto.response.OwnerApplicationResponse;
import com.sportbooking.entity.OwnerProfile;
import com.sportbooking.entity.User;
import com.sportbooking.entity.enums.ApprovalStatus;
import com.sportbooking.entity.enums.Role;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.mapper.UserMapper;
import com.sportbooking.repository.OwnerProfileRepository;
import com.sportbooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Luồng trở thành chủ sân:
 * CUSTOMER gửi yêu cầu (PENDING) -> ADMIN duyệt (APPROVED, user.role = OWNER)
 *                                 -> hoặc từ chối (REJECTED, có lý do) -> CUSTOMER sửa và gửi lại.
 */
@Service
@RequiredArgsConstructor
public class OwnerApplicationService {

    private final OwnerProfileRepository ownerProfileRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public OwnerApplicationResponse apply(Long userId, OwnerApplicationRequest request) {
        User user = findUser(userId);
        if (user.getRole() != Role.CUSTOMER) {
            throw new AppException(ErrorCode.OWNER_APPLICATION_NOT_ALLOWED);
        }

        String businessName = request.businessName().trim();
        Optional<OwnerProfile> existing = ownerProfileRepository.findById(userId);

        OwnerProfile profile;
        if (existing.isEmpty()) {
            profile = ownerProfileRepository.save(new OwnerProfile(user, businessName));
        } else if (existing.get().getApprovalStatus() == ApprovalStatus.REJECTED) {
            profile = existing.get();
            profile.resubmit(businessName);
        } else {
            // Đang PENDING hoặc đã APPROVED
            throw new AppException(ErrorCode.OWNER_APPLICATION_ALREADY_EXISTS);
        }
        return UserMapper.toResponse(profile);
    }

    @Transactional(readOnly = true)
    public OwnerApplicationResponse getMyApplication(Long userId) {
        return UserMapper.toResponse(findProfile(userId));
    }

    // ===== Dành cho ADMIN =====

    @Transactional(readOnly = true)
    public PageResponse<OwnerApplicationResponse> search(ApprovalStatus status, Pageable pageable) {
        Page<OwnerProfile> page = status == null
                ? ownerProfileRepository.findAll(pageable)
                : ownerProfileRepository.findByApprovalStatus(status, pageable);
        return PageResponse.from(page.map(UserMapper::toResponse));
    }

    @Transactional
    public OwnerApplicationResponse approve(Long adminId, Long applicantUserId) {
        OwnerProfile profile = findPendingProfile(applicantUserId);
        profile.approve(userRepository.getReferenceById(adminId), clock.instant());
        // Cùng 1 transaction: duyệt hồ sơ và đổi role phải cùng thành công hoặc cùng thất bại
        profile.getUser().promoteToOwner();
        return UserMapper.toResponse(profile);
    }

    @Transactional
    public OwnerApplicationResponse reject(Long adminId, Long applicantUserId, String reason) {
        OwnerProfile profile = findPendingProfile(applicantUserId);
        profile.reject(userRepository.getReferenceById(adminId), reason.trim(), clock.instant());
        return UserMapper.toResponse(profile);
    }

    private OwnerProfile findPendingProfile(Long userId) {
        OwnerProfile profile = findProfile(userId);
        if (!profile.isPending()) {
            throw new AppException(ErrorCode.OWNER_APPLICATION_NOT_PENDING);
        }
        return profile;
    }

    private OwnerProfile findProfile(Long userId) {
        return ownerProfileRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu làm chủ sân"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng id=" + userId));
    }
}
