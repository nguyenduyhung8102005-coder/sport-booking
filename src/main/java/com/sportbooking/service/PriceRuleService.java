package com.sportbooking.service;

import com.sportbooking.dto.request.PriceRuleItem;
import com.sportbooking.dto.response.PriceRuleResponse;
import com.sportbooking.entity.Court;
import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.PriceLabel;
import com.sportbooking.mapper.VenueMapper;
import com.sportbooking.repository.PriceRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceRuleService {

    private final PriceRuleRepository priceRuleRepository;
    private final VenueManagementService venueManagementService;

    @Transactional(readOnly = true)
    public List<PriceRuleResponse> getPriceRules(Long ownerId, Long courtId) {
        venueManagementService.findOwnedCourt(ownerId, courtId);   // kiểm tra quyền sở hữu
        return priceRuleRepository.findByCourtIdOrderByDayTypeAscStartTimeAsc(courtId).stream()
                .map(VenueMapper::toPriceRuleResponse)
                .toList();
    }

    /**
     * Thay toàn bộ bảng giá trong 1 transaction: xóa hết cũ -> thêm mới.
     * Nếu thêm mới lỗi giữa chừng, lệnh xóa cũng bị rollback -> không bao giờ có trạng thái "mất giá".
     *
     * Cách khác: so sánh từng dòng cũ/mới để chỉ UPDATE dòng thay đổi -> phức tạp hơn nhiều,
     * trong khi bảng giá 1 sân chỉ vài chục dòng và hiếm khi sửa.
     */
    @Transactional
    public List<PriceRuleResponse> replacePriceRules(Long ownerId, Long courtId, List<PriceRuleItem> items) {
        Court court = venueManagementService.findOwnedCourt(ownerId, courtId);
        Venue venue = court.getVenue();
        PriceRuleValidator.validate(items, venue.getOpenTime(), venue.getCloseTime());

        priceRuleRepository.deleteByCourtId(courtId);
        List<PriceRule> newRules = items.stream()
                .map(item -> new PriceRule(
                        court,
                        item.dayType(),
                        item.startTime(),
                        item.endTime(),
                        item.pricePerHour(),
                        item.label() == null ? PriceLabel.NORMAL : item.label()))
                .toList();
        priceRuleRepository.saveAll(newRules);

        return newRules.stream()
                .sorted(Comparator.comparing(PriceRule::getDayType).thenComparing(PriceRule::getStartTime))
                .map(VenueMapper::toPriceRuleResponse)
                .toList();
    }
}
