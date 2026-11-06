package com.sportbooking.service;

import com.sportbooking.common.DatabaseErrors;
import com.sportbooking.common.PageResponse;
import com.sportbooking.common.PageableUtils;
import com.sportbooking.config.BookingProperties;
import com.sportbooking.dto.request.CreateBookingRequest;
import com.sportbooking.dto.response.BookingResponse;
import com.sportbooking.entity.Booking;
import com.sportbooking.entity.BookingSlot;
import com.sportbooking.entity.Court;
import com.sportbooking.entity.PriceRule;
import com.sportbooking.entity.Venue;
import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.entity.enums.DayType;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.mapper.BookingMapper;
import com.sportbooking.repository.BookingRepository;
import com.sportbooking.repository.BookingSlotRepository;
import com.sportbooking.repository.PriceRuleRepository;
import com.sportbooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ĐẶT SÂN VÀ CHỐNG ĐẶT TRÙNG (double booking)
 *
 * Chiến lược đã chọn: UNIQUE CONSTRAINT trên booking_slots (court_id, slot_date, slot_start).
 *
 * Mỗi lượt đặt được tách thành các slot 30 phút và INSERT vào booking_slots trong CÙNG transaction
 * với booking. Nếu 2 request cùng muốn slot 17:30 của sân 3:
 *   - Request A INSERT slot 17:30 trước -> PostgreSQL ghi nhận (chưa commit).
 *   - Request B INSERT slot 17:30 -> PostgreSQL thấy có người đang giữ khóa này, B phải CHỜ.
 *   - A commit -> B nhận lỗi "duplicate key" -> B rollback toàn bộ (cả dòng booking của B).
 *   - (Nếu A rollback thay vì commit -> B được INSERT bình thường.)
 * Đảm bảo này do DATABASE thực hiện, nên đúng cả khi chạy nhiều instance app cùng lúc.
 *
 * So sánh với pessimistic / optimistic lock: xem LockingStrategiesIntegrationTest và README.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    static final String SLOT_UNIQUE_CONSTRAINT = "uk_booking_slots_court_date_start";
    private static final List<String> SORTABLE = List.of("createdAt", "bookingDate");

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final UserRepository userRepository;
    private final CourtAvailabilityService courtAvailabilityService;
    private final BookingSlotReleaser bookingSlotReleaser;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    @Transactional
    public BookingResponse createBooking(Long customerId, CreateBookingRequest request) {
        Instant nowInstant = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, bookingProperties.zone());

        // 1. Sân có đang nhận đặt không
        Court court = courtAvailabilityService.findBookableCourt(request.courtId());
        Venue venue = court.getVenue();

        // 2. Quy tắc thời gian: không đặt quá khứ, trong giờ mở cửa, đủ thời lượng tối thiểu...
        BookingTimeValidator.validate(request.date(), request.startTime(), request.endTime(), court.getSportType(),
                venue.getOpenTime(), venue.getCloseTime(), now,
                bookingProperties.maxAdvanceDays(), bookingProperties.maxSlotsPerBooking());

        // 3. Tính giá phía server (mọi slot phải có giá)
        List<LocalTime> slotStarts = BookingSlotCalculator.slotStarts(request.startTime(), request.endTime());
        List<PriceRule> rules = priceRuleRepository.findByCourtIdOrderByDayTypeAscStartTimeAsc(court.getId());
        long totalPrice = BookingSlotCalculator.totalPrice(slotStarts, DayType.of(request.date()), rules);

        // 4. Chống giữ chỗ ảo. Đây là giới hạn "mềm": vài request song song của CÙNG 1 khách
        //    vẫn có thể vượt nhẹ, chấp nhận được vì GĐ 5 sẽ tự hủy các lượt quá hạn.
        long pending = bookingRepository.countByCustomerIdAndStatusAndExpiresAtAfter(
                customerId, BookingStatus.PENDING, nowInstant);
        if (pending >= bookingProperties.maxPendingPerCustomer()) {
            throw new AppException(ErrorCode.TOO_MANY_PENDING_BOOKINGS);
        }

        // 5. Kiểm tra trước: CHỈ để báo lỗi dễ hiểu ("17:30 đã có người đặt").
        //    Bước này KHÔNG chống được đặt trùng: 2 request song song đều có thể thấy "còn trống".
        List<LocalTime> taken = bookingSlotRepository.findTakenSlotStarts(court.getId(), request.date(), slotStarts);
        if (!taken.isEmpty()) {
            throw new AppException(ErrorCode.SLOT_ALREADY_BOOKED,
                    "Khung giờ " + taken.stream().map(LocalTime::toString).collect(Collectors.joining(", "))
                            + " đã có người đặt, vui lòng chọn giờ khác");
        }

        // 6. Lưu booking + các slot trong cùng transaction. UNIQUE constraint là lớp bảo vệ THẬT SỰ.
        Booking booking = bookingRepository.save(Booking.newPending(
                BookingCodeGenerator.generate(now.toLocalDate()),
                userRepository.getReferenceById(customerId),
                court,
                request.date(),
                request.startTime(),
                request.endTime(),
                totalPrice,
                nowInstant.plus(bookingProperties.holdDuration())));

        // slotStarts đã tăng dần -> mọi request INSERT slot theo CÙNG một thứ tự.
        // Nhờ vậy không có deadlock: không thể xảy ra cảnh A giữ 17:00 chờ 17:30
        // trong khi B giữ 17:30 lại chờ 17:00.
        List<BookingSlot> slots = slotStarts.stream()
                .map(start -> new BookingSlot(booking, court, request.date(), start))
                .toList();
        try {
            // flush NGAY để lỗi trùng xảy ra tại đây (trong try), không phải lúc commit
            bookingSlotRepository.saveAllAndFlush(slots);
        } catch (DataIntegrityViolationException e) {
            if (DatabaseErrors.isConstraintViolation(e, SLOT_UNIQUE_CONSTRAINT)) {
                // Thua cuộc đua: request khác vừa giữ slot này. Ném lỗi -> Spring rollback cả booking.
                log.info("Slot conflict on court={} date={} {}-{}", court.getId(), request.date(),
                        request.startTime(), request.endTime());
                throw new AppException(ErrorCode.SLOT_ALREADY_BOOKED);
            }
            throw e;   // lỗi ràng buộc khác: không nuốt, để GlobalExceptionHandler xử lý
        }

        return BookingMapper.toResponse(booking);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookings(Long customerId, BookingStatus status, Pageable pageable) {
        Pageable safePageable = PageableUtils.withAllowedSort(pageable, SORTABLE);
        Page<Booking> page = status == null
                ? bookingRepository.findByCustomerId(customerId, safePageable)
                : bookingRepository.findByCustomerIdAndStatus(customerId, status, safePageable);
        return PageResponse.from(page.map(BookingMapper::toResponse));
    }

    /**
     * Khách tự hủy lượt đặt của mình. Nếu job hết hạn (hoặc thanh toán) sửa booking này cùng lúc,
     * một bên sẽ thua bằng OptimisticLockingFailureException -> GlobalExceptionHandler trả 409.
     */
    @Transactional
    public BookingResponse cancelMyBooking(Long customerId, Long bookingId, String reason) {
        Booking booking = bookingRepository.findByIdAndCustomerId(bookingId, customerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lượt đặt id=" + bookingId));

        Instant nowInstant = clock.instant();
        CancellationPolicy.checkCustomerCancellation(booking.getStatus(), booking.startDateTime(),
                LocalDateTime.ofInstant(nowInstant, bookingProperties.zone()), bookingProperties.cancelDeadline());

        booking.cancel(reason == null || reason.isBlank() ? "Khách hủy" : reason.trim(), nowInstant);
        bookingSlotReleaser.releaseSlots(booking);
        return BookingMapper.toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getMyBooking(Long customerId, Long bookingId) {
        return bookingRepository.findByIdAndCustomerId(bookingId, customerId)
                .map(BookingMapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lượt đặt id=" + bookingId));
    }
}
