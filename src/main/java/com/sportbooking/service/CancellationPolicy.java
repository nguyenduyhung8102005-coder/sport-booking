package com.sportbooking.service;

import com.sportbooking.entity.enums.BookingStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Chính sách hủy lượt đặt. Java thuần, "now" truyền từ ngoài vào -> unit test mọi tình huống thời gian.
 *
 *  Khách hủy:
 *   - PENDING (chưa trả tiền): hủy lúc nào cũng được, miễn chưa tới giờ chơi.
 *   - CONFIRMED (đã trả tiền): chỉ được hủy trước giờ chơi ít nhất cancelDeadline (mặc định 24h),
 *     để chủ sân còn kịp cho người khác thuê.
 *  Chủ sân hủy (sân hỏng, mất điện...): PENDING hoặc CONFIRMED, miễn chưa tới giờ chơi, bắt buộc ghi lý do.
 *
 *  Hoàn tiền cho lượt CONFIRMED bị hủy: phần mở rộng (VNPay refund API), MVP xử lý thủ công.
 */
public final class CancellationPolicy {

    private CancellationPolicy() {
    }

    public static void checkCustomerCancellation(BookingStatus status, LocalDateTime startAt,
                                                 LocalDateTime now, Duration cancelDeadline) {
        checkCancellableState(status);
        checkNotStarted(startAt, now);
        if (status == BookingStatus.CONFIRMED && now.isAfter(startAt.minus(cancelDeadline))) {
            throw new AppException(ErrorCode.CANCELLATION_NOT_ALLOWED,
                    "Lượt đặt đã thanh toán chỉ được hủy trước giờ chơi ít nhất "
                            + cancelDeadline.toHours() + " giờ");
        }
    }

    public static void checkOwnerCancellation(BookingStatus status, LocalDateTime startAt, LocalDateTime now) {
        checkCancellableState(status);
        checkNotStarted(startAt, now);
    }

    private static void checkCancellableState(BookingStatus status) {
        if (status != BookingStatus.PENDING && status != BookingStatus.CONFIRMED) {
            throw new AppException(ErrorCode.INVALID_BOOKING_STATE,
                    "Không thể hủy lượt đặt đang ở trạng thái " + status);
        }
    }

    private static void checkNotStarted(LocalDateTime startAt, LocalDateTime now) {
        if (!now.isBefore(startAt)) {
            throw new AppException(ErrorCode.CANCELLATION_NOT_ALLOWED, "Không thể hủy lượt đặt đã tới giờ chơi");
        }
    }
}
