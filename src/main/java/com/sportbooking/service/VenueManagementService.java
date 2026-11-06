package com.sportbooking.service;

import com.sportbooking.common.PageResponse;
import com.sportbooking.common.PageableUtils;
import com.sportbooking.dto.request.CourtCreateRequest;
import com.sportbooking.dto.request.CourtUpdateRequest;
import com.sportbooking.dto.request.VenueRequest;
import com.sportbooking.dto.response.CourtResponse;
import com.sportbooking.dto.response.VenueDetailResponse;
import com.sportbooking.dto.response.VenueResponse;
import com.sportbooking.entity.Court;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.VenueStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.mapper.VenueMapper;
import com.sportbooking.repository.CourtRepository;
import com.sportbooking.repository.PriceRuleRepository;
import com.sportbooking.repository.UserRepository;
import com.sportbooking.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Chủ sân quản lý cụm sân và sân con CỦA MÌNH.
 *
 * Mọi thao tác đều tìm venue/court kèm điều kiện ownerId. Không thuộc về mình -> 404 (không phải 403):
 * trả 403 sẽ vô tình xác nhận "id này có tồn tại, chỉ là không phải của bạn".
 */
@Service
@RequiredArgsConstructor
public class VenueManagementService {

    private static final List<String> OWNER_SORTABLE = List.of("createdAt", "name");

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final UserRepository userRepository;
    private final VenueQueryService venueQueryService;

    // ===== Cụm sân =====

    @Transactional
    public VenueResponse createVenue(Long ownerId, VenueRequest request) {
        validateOpeningHours(request);
        Venue venue = new Venue(
                userRepository.getReferenceById(ownerId),   // chỉ cần id làm khóa ngoại, không SELECT user
                request.name().trim(),
                trimToNull(request.description()),
                request.address().trim(),
                request.district().trim(),
                request.city().trim(),
                request.openTime(),
                request.closeTime());
        return VenueMapper.toResponse(venueRepository.save(venue));
    }

    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> listMyVenues(Long ownerId, Pageable pageable) {
        return PageResponse.from(venueRepository
                .findByOwnerId(ownerId, PageableUtils.withAllowedSort(pageable, OWNER_SORTABLE))
                .map(VenueMapper::toResponse));
    }

    /** Chủ sân xem được cả sân đang bảo trì (khách thì không). */
    @Transactional(readOnly = true)
    public VenueDetailResponse getMyVenue(Long ownerId, Long venueId) {
        return venueQueryService.buildDetail(findOwnedVenue(ownerId, venueId), false);
    }

    @Transactional
    public VenueResponse updateVenue(Long ownerId, Long venueId, VenueRequest request) {
        validateOpeningHours(request);
        Venue venue = findOwnedVenue(ownerId, venueId);
        venue.updateInfo(
                request.name().trim(),
                trimToNull(request.description()),
                request.address().trim(),
                request.district().trim(),
                request.city().trim(),
                request.openTime(),
                request.closeTime());
        return VenueMapper.toResponse(venue);
    }

    @Transactional
    public VenueResponse changeVenueStatus(Long ownerId, Long venueId, VenueStatus status) {
        Venue venue = findOwnedVenue(ownerId, venueId);
        venue.changeStatus(status);
        return VenueMapper.toResponse(venue);
    }

    // ===== Sân con =====

    @Transactional
    public CourtResponse addCourt(Long ownerId, Long venueId, CourtCreateRequest request) {
        Venue venue = findOwnedVenue(ownerId, venueId);
        String name = request.name().trim();
        if (courtRepository.existsByVenueIdAndName(venueId, name)) {
            throw new AppException(ErrorCode.COURT_NAME_ALREADY_EXISTS);
        }
        Court court = new Court(venue, name, request.sportType());
        saveCourtOrThrowDuplicate(court);
        return VenueMapper.toCourtResponse(court, List.of());
    }

    @Transactional
    public CourtResponse updateCourt(Long ownerId, Long courtId, CourtUpdateRequest request) {
        Court court = findOwnedCourt(ownerId, courtId);
        String name = request.name().trim();
        if (!name.equals(court.getName()) && courtRepository.existsByVenueIdAndName(court.getVenue().getId(), name)) {
            throw new AppException(ErrorCode.COURT_NAME_ALREADY_EXISTS);
        }
        court.update(name, request.status());
        // GĐ 4/5: chuyển sang MAINTENANCE khi đã có lượt đặt trong tương lai -> cần xử lý các booking đó
        saveCourtOrThrowDuplicate(court);
        return VenueMapper.toCourtResponse(court, priceRuleRepository.findByCourtIdOrderByDayTypeAscStartTimeAsc(courtId));
    }

    /**
     * public (không để package-private): service này được Spring bọc trong proxy vì có @Transactional.
     * Gọi method không-public qua proxy có thể chạy trên chính object proxy, nơi các repository là null.
     */
    public Court findOwnedCourt(Long ownerId, Long courtId) {
        return courtRepository.findOwnedCourt(courtId, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sân id=" + courtId));
    }

    private Venue findOwnedVenue(Long ownerId, Long venueId) {
        return venueRepository.findByIdAndOwnerId(venueId, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cụm sân id=" + venueId));
    }

    /** Cùng kỹ thuật như đăng ký email ở GĐ 2: kiểm tra trước cho lỗi thân thiện, UNIQUE ở DB chặn cuối. */
    private void saveCourtOrThrowDuplicate(Court court) {
        try {
            courtRepository.saveAndFlush(court);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.COURT_NAME_ALREADY_EXISTS);
        }
    }

    private static void validateOpeningHours(VenueRequest request) {
        if (!request.closeTime().isAfter(request.openTime())) {
            throw new AppException(ErrorCode.INVALID_OPENING_HOURS);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
