package com.sportbooking.service;

import com.sportbooking.common.PageResponse;
import com.sportbooking.common.PageableUtils;
import com.sportbooking.dto.request.VenueSearchRequest;
import com.sportbooking.dto.response.VenueDetailResponse;
import com.sportbooking.dto.response.VenueSummaryResponse;
import com.sportbooking.entity.Court;
import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.CourtStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.mapper.VenueMapper;
import com.sportbooking.repository.CourtRepository;
import com.sportbooking.repository.PriceRuleRepository;
import com.sportbooking.repository.VenueRepository;
import com.sportbooking.repository.VenueSpecifications;
import com.sportbooking.repository.projection.VenueSportStat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Các thao tác ĐỌC dữ liệu sân: tìm kiếm và xem chi tiết (dùng cho khách, không cần đăng nhập).
 */
@Service
@RequiredArgsConstructor
public class VenueQueryService {

    private static final List<String> SEARCH_SORTABLE = List.of("avgRating", "reviewCount", "name", "createdAt");

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final PriceRuleRepository priceRuleRepository;

    /**
     * Tổng cộng 3 câu SQL cho mỗi trang, dù trang có 10 hay 100 cụm sân:
     *   1. SELECT các cụm sân của trang (WHERE + ORDER BY + LIMIT/OFFSET)
     *   2. SELECT COUNT(*) để biết tổng số kết quả (totalElements, totalPages)
     *   3. SELECT thống kê loại sân + giá cho tất cả cụm sân trong trang (WHERE venue_id IN ...)
     */
    @Transactional(readOnly = true)
    public PageResponse<VenueSummaryResponse> search(VenueSearchRequest criteria, Pageable pageable) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice() > criteria.maxPrice()) {
            throw new AppException(ErrorCode.INVALID_PRICE_RANGE);
        }

        Specification<Venue> spec = Specification.allOf(
                VenueSpecifications.isPubliclyVisible(),
                VenueSpecifications.nameContains(criteria.keyword()),
                VenueSpecifications.cityEquals(criteria.city()),
                VenueSpecifications.districtEquals(criteria.district()),
                VenueSpecifications.hasMatchingCourt(
                        criteria.sportType(), criteria.dayType(), criteria.minPrice(), criteria.maxPrice()));

        Page<Venue> page = venueRepository.findAll(spec, PageableUtils.withAllowedSort(pageable, SEARCH_SORTABLE));

        Map<Long, List<VenueSportStat>> statsByVenueId = loadSportStats(page.getContent());
        return PageResponse.from(page.map(venue ->
                VenueMapper.toSummary(venue, statsByVenueId.getOrDefault(venue.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public VenueDetailResponse getPublicDetail(Long venueId) {
        Venue venue = venueRepository.findById(venueId)
                .filter(Venue::isPubliclyVisible)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cụm sân id=" + venueId));
        return buildDetail(venue, true);
    }

    /**
     * Ghép chi tiết cụm sân bằng 2 câu query (sân con + bảng giá của tất cả sân con),
     * thay vì 1 + N câu (mỗi sân con 1 câu lấy bảng giá).
     * Dùng chung cho trang public (chỉ sân ACTIVE) và trang quản lý của chủ sân (mọi sân).
     */
    @Transactional(readOnly = true)
    public VenueDetailResponse buildDetail(Venue venue, boolean activeCourtsOnly) {
        List<Court> courts = courtRepository.findByVenueIdOrderByNameAsc(venue.getId()).stream()
                .filter(court -> !activeCourtsOnly || court.getStatus() == CourtStatus.ACTIVE)
                .toList();

        Map<Long, List<PriceRule>> rulesByCourtId = courts.isEmpty()
                ? Map.of()
                : priceRuleRepository.findByCourtIdInOrderByDayTypeAscStartTimeAsc(
                                courts.stream().map(Court::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(rule -> rule.getCourt().getId()));

        return VenueMapper.toDetail(venue, courts, rulesByCourtId);
    }

    private Map<Long, List<VenueSportStat>> loadSportStats(List<Venue> venues) {
        if (venues.isEmpty()) {
            return Map.of();   // tránh câu "WHERE venue_id IN ()" rỗng
        }
        List<Long> venueIds = venues.stream().map(Venue::getId).toList();
        return courtRepository.findSportStats(venueIds, CourtStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(VenueSportStat::venueId));
    }
}
