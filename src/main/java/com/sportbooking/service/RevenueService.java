package com.sportbooking.service;

import com.sportbooking.dto.response.RevenueReportResponse;
import com.sportbooking.dto.response.RevenueReportResponse.Item;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.VenueRepository;
import com.sportbooking.repository.projection.DailyRevenue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Thống kê doanh thu cho chủ sân. Toàn bộ phép cộng làm trong DB (GROUP BY), Java chỉ định dạng kết quả. */
@Service
@RequiredArgsConstructor
public class RevenueService {

    /** Doanh thu = lượt đã trả tiền và không bị hủy. Lượt bị hủy sau khi trả tiền sẽ được hoàn lại. */
    static final List<BookingStatus> PAID_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
    private static final long MAX_DAYS = 366;

    private final BookingRepository bookingRepository;
    private final VenueRepository venueRepository;

    @Transactional(readOnly = true)
    public RevenueReportResponse getRevenue(Long ownerId, Long venueId, LocalDate from, LocalDate to, RevenueGroupBy groupBy) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE, "Khoảng thời gian phải hợp lệ và không quá " + MAX_DAYS + " ngày");
        }
        venueRepository.findByIdAndOwnerId(venueId, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cụm sân id=" + venueId));

        List<Item> items = groupBy == RevenueGroupBy.DAY
                ? fillMissingDays(from, to, bookingRepository.sumRevenueByDay(venueId, PAID_STATUSES, from, to))
                : bookingRepository.sumRevenueByCourt(venueId, PAID_STATUSES, from, to).stream()
                        .map(row -> new Item(null, row.courtId(), row.courtName(), row.bookingCount(), row.revenue()))
                        .toList();

        long totalBookings = items.stream().mapToLong(Item::bookingCount).sum();
        long totalRevenue = items.stream().mapToLong(Item::revenue).sum();
        return new RevenueReportResponse(venueId, from, to, groupBy, totalBookings, totalRevenue, items);
    }

    /**
     * GROUP BY chỉ trả về những ngày CÓ doanh thu. Biểu đồ phía frontend cần đủ mọi ngày,
     * nên điền 0 cho ngày trống. (Cách khác: generate_series của PostgreSQL + LEFT JOIN.)
     */
    static List<Item> fillMissingDays(LocalDate from, LocalDate to, List<DailyRevenue> rows) {
        Map<LocalDate, DailyRevenue> byDate = rows.stream()
                .collect(Collectors.toMap(DailyRevenue::date, Function.identity()));
        List<Item> items = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            DailyRevenue row = byDate.get(day);
            items.add(row == null
                    ? new Item(day, null, null, 0, 0)
                    : new Item(day, null, null, row.bookingCount(), row.revenue()));
        }
        return items;
    }
}
