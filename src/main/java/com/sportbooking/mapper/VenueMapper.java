package com.sportbooking.mapper;

import com.sportbooking.dto.response.CourtResponse;
import com.sportbooking.dto.response.PriceRuleResponse;
import com.sportbooking.dto.response.VenueDetailResponse;
import com.sportbooking.dto.response.VenueResponse;
import com.sportbooking.dto.response.VenueSummaryResponse;
import com.sportbooking.entity.Court;
import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.Venue;
import com.sportbooking.repository.projection.VenueSportStat;

import java.util.List;
import java.util.Map;

/** Map tay entity -> DTO (không dùng MapStruct). */
public final class VenueMapper {

    private VenueMapper() {
    }

    public static VenueResponse toResponse(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getDescription(),
                venue.getAddress(),
                venue.getDistrict(),
                venue.getCity(),
                venue.getOpenTime(),
                venue.getCloseTime(),
                venue.getStatus(),
                venue.getAvgRating(),
                venue.getReviewCount(),
                venue.getCreatedAt());
    }

    public static VenueSummaryResponse toSummary(Venue venue, List<VenueSportStat> stats) {
        List<VenueSummaryResponse.SportSummary> sports = stats.stream()
                .map(s -> new VenueSummaryResponse.SportSummary(
                        s.sportType(), s.courtCount(), s.minPricePerHour(), s.maxPricePerHour()))
                .toList();
        return new VenueSummaryResponse(
                venue.getId(),
                venue.getName(),
                venue.getAddress(),
                venue.getDistrict(),
                venue.getCity(),
                venue.getOpenTime(),
                venue.getCloseTime(),
                venue.getAvgRating(),
                venue.getReviewCount(),
                sports);
    }

    public static VenueDetailResponse toDetail(Venue venue, List<Court> courts,
                                               Map<Long, List<PriceRule>> rulesByCourtId) {
        List<CourtResponse> courtResponses = courts.stream()
                .map(court -> toCourtResponse(court, rulesByCourtId.getOrDefault(court.getId(), List.of())))
                .toList();
        return new VenueDetailResponse(
                venue.getId(),
                venue.getName(),
                venue.getDescription(),
                venue.getAddress(),
                venue.getDistrict(),
                venue.getCity(),
                venue.getOpenTime(),
                venue.getCloseTime(),
                venue.getStatus(),
                venue.getAvgRating(),
                venue.getReviewCount(),
                courtResponses);
    }

    public static CourtResponse toCourtResponse(Court court, List<PriceRule> rules) {
        return new CourtResponse(
                court.getId(),
                court.getName(),
                court.getSportType(),
                court.getStatus(),
                rules.stream().map(VenueMapper::toPriceRuleResponse).toList());
    }

    public static PriceRuleResponse toPriceRuleResponse(PriceRule rule) {
        return new PriceRuleResponse(
                rule.getDayType(),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.getPricePerHour(),
                rule.getLabel());
    }
}
