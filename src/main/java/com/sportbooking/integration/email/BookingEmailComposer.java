package com.sportbooking.integration.email;

import com.sportbooking.event.BookingEmailData;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Soạn tiêu đề và nội dung email. Java thuần -> unit test được, không cần máy chủ mail.
 * (Email HTML đẹp hơn: dùng Thymeleaf template, phần mở rộng.)
 */
public final class BookingEmailComposer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private BookingEmailComposer() {
    }

    public record EmailContent(String subject, String body) {
    }

    public static EmailContent confirmed(BookingEmailData data) {
        String subject = "[Sport Booking] Xác nhận đặt sân " + data.bookingCode();
        String body = """
                Xin chào %s,

                Thanh toán thành công, lượt đặt sân của bạn đã được XÁC NHẬN.

                %s

                Vui lòng đọc mã đặt sân cho chủ sân khi tới nơi.
                Bạn có thể tự hủy lượt đặt trước giờ chơi ít nhất 24 giờ.

                Chúc bạn có trận đấu vui vẻ!
                Sport Booking
                """.formatted(data.customerName(), details(data));
        return new EmailContent(subject, body);
    }

    public static EmailContent cancelledByOwner(BookingEmailData data, String reason) {
        String subject = "[Sport Booking] Lượt đặt " + data.bookingCode() + " đã bị chủ sân hủy";
        String body = """
                Xin chào %s,

                Rất tiếc, chủ sân đã hủy lượt đặt của bạn.
                Lý do: %s

                %s

                Nếu bạn đã thanh toán, chúng tôi sẽ liên hệ để hoàn tiền.
                Sport Booking
                """.formatted(data.customerName(), reason, details(data));
        return new EmailContent(subject, body);
    }

    private static String details(BookingEmailData data) {
        return """
                Mã đặt sân : %s
                Cụm sân    : %s
                Địa chỉ    : %s
                Sân        : %s
                Thời gian  : %s - %s ngày %s
                Thành tiền : %s""".formatted(
                data.bookingCode(), data.venueName(), data.venueAddress(), data.courtName(),
                data.startTime(), data.endTime(), DATE.format(data.date()), formatVnd(data.totalPrice()));
    }

    /** 1500000 -> "1.500.000đ" */
    static String formatVnd(long amount) {
        return String.format(VIETNAMESE, "%,dđ", amount);
    }
}
