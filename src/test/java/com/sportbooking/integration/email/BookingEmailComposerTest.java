package com.sportbooking.integration.email;

import com.sportbooking.event.BookingEmailData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingEmailComposerTest {

    private static final BookingEmailData DATA = new BookingEmailData(
            "BK261107K7XM2Q", "an@gmail.com", "Nguyễn An", "Sân bóng Mỹ Đình Arena", "Số 1 Lê Đức Thọ",
            "Sân 5-1", LocalDate.of(2026, 11, 7), LocalTime.of(17, 0), LocalTime.of(18, 30), 1_500_000);

    @Test
    void confirmedEmail_containsAllBookingDetails_inVietnameseFormat() {
        BookingEmailComposer.EmailContent email = BookingEmailComposer.confirmed(DATA);

        assertThat(email.subject()).contains("BK261107K7XM2Q");
        assertThat(email.body())
                .contains("Nguyễn An")
                .contains("Sân bóng Mỹ Đình Arena")
                .contains("17:00 - 18:30 ngày 07/11/2026")
                .contains("1.500.000đ");
    }

    @Test
    void cancelledByOwnerEmail_containsReason() {
        BookingEmailComposer.EmailContent email = BookingEmailComposer.cancelledByOwner(DATA, "Sân bảo trì");

        assertThat(email.subject()).contains("hủy");
        assertThat(email.body()).contains("Lý do: Sân bảo trì").contains("BK261107K7XM2Q");
    }

    @Test
    void formatVnd_usesDotAsThousandsSeparator() {
        assertThat(BookingEmailComposer.formatVnd(500_000)).isEqualTo("500.000đ");
        assertThat(BookingEmailComposer.formatVnd(0)).isEqualTo("0đ");
    }
}
