package com.sportbooking.service;

import com.sportbooking.dto.request.PriceRuleItem;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kiểm tra các quy tắc nghiệp vụ của bảng giá mà annotation không diễn tả được
 * (liên quan tới nhiều dòng cùng lúc, hoặc tới giờ mở cửa của cụm sân).
 *
 * Tách thành class thuần (không phụ thuộc Spring, DB) -> unit test cực dễ.
 */
public final class PriceRuleValidator {

    private PriceRuleValidator() {
    }

    public static void validate(List<PriceRuleItem> rules, LocalTime openTime, LocalTime closeTime) {
        for (PriceRuleItem rule : rules) {
            if (!rule.endTime().isAfter(rule.startTime())) {
                fail("Khung %s phải có giờ kết thúc sau giờ bắt đầu", describe(rule));
            }
            if (rule.startTime().isBefore(openTime) || rule.endTime().isAfter(closeTime)) {
                fail("Khung %s nằm ngoài giờ mở cửa %s–%s", describe(rule), openTime, closeTime);
            }
            if (rule.pricePerHour() % 1000 != 0) {
                fail("Giá của khung %s phải là bội số của 1.000đ", describe(rule));
            }
        }

        // Kiểm tra chồng lấn trong từng loại ngày: sắp xếp theo giờ bắt đầu,
        // rồi so mỗi khung với khung ngay trước nó. Hai loại ngày khác nhau thì không ảnh hưởng nhau.
        Map<DayType, List<PriceRuleItem>> byDayType = rules.stream()
                .collect(Collectors.groupingBy(PriceRuleItem::dayType));
        for (List<PriceRuleItem> sameDay : byDayType.values()) {
            List<PriceRuleItem> sorted = sameDay.stream()
                    .sorted(Comparator.comparing(PriceRuleItem::startTime))
                    .toList();
            for (int i = 1; i < sorted.size(); i++) {
                PriceRuleItem previous = sorted.get(i - 1);
                PriceRuleItem current = sorted.get(i);
                // [a, b) và [c, d) với c >= a chồng lấn khi c < b
                if (current.startTime().isBefore(previous.endTime())) {
                    fail("Khung %s chồng lấn với khung %s", describe(current), describe(previous));
                }
            }
        }
    }

    private static String describe(PriceRuleItem rule) {
        return rule.dayType().getDisplayName() + " " + rule.startTime() + "–" + rule.endTime();
    }

    private static void fail(String template, Object... args) {
        throw new AppException(ErrorCode.INVALID_PRICE_RULES, String.format(template, args));
    }
}
