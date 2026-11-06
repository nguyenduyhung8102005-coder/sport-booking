package com.sportbooking.service;

import com.sportbooking.support.ConcurrencyRunner;
import com.sportbooking.support.ConcurrencyRunner.Outcome;
import com.sportbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalTime;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🧪 PHÒNG THÍ NGHIỆM: so sánh 4 cách xử lý "50 người cùng đặt slot 17:00" trên PostgreSQL thật.
 *
 * Dùng bảng demo riêng (demo_*) và SQL thuần qua JdbcTemplate để thấy rõ từng câu lệnh,
 * không bị JPA che mất. Code đặt sân thật (BookingService) dùng cách 1: UNIQUE CONSTRAINT.
 *
 *   0. Kiểm tra rồi ghi (check-then-act), không bảo vệ  -> SAI: tạo nhiều booking trùng
 *   1. UNIQUE constraint                               -> đúng, các slot khác nhau chạy song song
 *   2. Pessimistic lock (SELECT ... FOR UPDATE)        -> đúng, nhưng xếp hàng TẤT CẢ request của sân
 *   3. Optimistic lock (cột version)                   -> đúng, nhưng request thua đã làm việc vô ích
 *
 * Chạy test và đọc dòng [LAB] in ra console để so sánh số dòng và thời gian.
 */
class LockingStrategiesIntegrationTest extends IntegrationTestBase {

    private static final int THREADS = 50;
    /** Giả lập thời gian xử lý giữa lúc "kiểm tra" và lúc "ghi" (gọi service khác, tính giá...). */
    private static final long THINK_TIME_MS = 50;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        jdbc.execute("CREATE TABLE IF NOT EXISTS demo_court (id INT PRIMARY KEY, version INT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS demo_booking (id SERIAL PRIMARY KEY, court_id INT NOT NULL, slot_start TIME NOT NULL)");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS demo_booking_unique (
                    id SERIAL PRIMARY KEY, court_id INT NOT NULL, slot_start TIME NOT NULL,
                    CONSTRAINT uk_demo_booking_unique UNIQUE (court_id, slot_start))""");
        jdbc.execute("TRUNCATE demo_booking, demo_booking_unique");
        jdbc.execute("DELETE FROM demo_court");
        jdbc.update("INSERT INTO demo_court (id, version) VALUES (1, 0)");
    }

    // ===================== 4 cách đặt 1 slot =====================

    /** 0. SAI: đọc thấy "còn trống" -> ghi. Nhiều request cùng đọc trước khi ai kịp ghi. */
    private boolean naiveCheckThenInsert(LocalTime slot) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            if (countBookings("demo_booking", slot) > 0) {
                return false;
            }
            pause();
            jdbc.update("INSERT INTO demo_booking (court_id, slot_start) VALUES (1, ?)", slot);
            return true;
        }));
    }

    /** 1. UNIQUE: cứ INSERT; database đảm bảo chỉ 1 dòng cho mỗi (court_id, slot_start). */
    private boolean uniqueConstraint(LocalTime slot) {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                pause();
                jdbc.update("INSERT INTO demo_booking_unique (court_id, slot_start) VALUES (1, ?)", slot);
                return true;
            }));
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /** 2. PESSIMISTIC: khóa dòng của sân trước khi kiểm tra. Ai đến sau phải đợi người trước commit. */
    private boolean pessimisticLock(LocalTime slot) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM demo_court WHERE id = 1 FOR UPDATE", Integer.class);
            if (countBookings("demo_booking", slot) > 0) {
                return false;
            }
            pause();
            jdbc.update("INSERT INTO demo_booking (court_id, slot_start) VALUES (1, ?)", slot);
            return true;
        }));
    }

    /**
     * 3. OPTIMISTIC: không khóa khi đọc. Lúc ghi, tăng version với điều kiện "version vẫn như lúc tôi đọc".
     * Nếu 0 dòng được cập nhật -> có người ghi trước -> hủy toàn bộ việc mình đã làm.
     */
    private boolean optimisticLock(LocalTime slot) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            Integer version = jdbc.queryForObject("SELECT version FROM demo_court WHERE id = 1", Integer.class);
            if (countBookings("demo_booking", slot) > 0) {
                return false;
            }
            pause();
            jdbc.update("INSERT INTO demo_booking (court_id, slot_start) VALUES (1, ?)", slot);
            int updated = jdbc.update("UPDATE demo_court SET version = version + 1 WHERE id = 1 AND version = ?", version);
            if (updated == 0) {
                status.setRollbackOnly();   // thua: bỏ cả dòng INSERT ở trên
                return false;
            }
            return true;
        }));
    }

    // ===================== Thí nghiệm 1: 50 người cùng đặt 17:00 =====================

    @Test
    void naiveCheckThenInsert_createsDuplicateBookings() throws InterruptedException {
        LabResult result = runSameSlot("0. check-then-act", this::naiveCheckThenInsert, "demo_booking");

        // Đây là BUG mà ta cần chống: nhiều hơn 1 người cùng "đặt thành công" slot 17:00
        assertThat(result.rows()).isGreaterThan(1);
    }

    @Test
    void uniqueConstraint_allowsExactlyOne() throws InterruptedException {
        LabResult result = runSameSlot("1. unique constraint", this::uniqueConstraint, "demo_booking_unique");

        assertThat(result.rows()).isEqualTo(1);
        assertThat(result.successes()).isEqualTo(1);
    }

    @Test
    void pessimisticLock_allowsExactlyOne() throws InterruptedException {
        LabResult result = runSameSlot("2. pessimistic lock", this::pessimisticLock, "demo_booking");

        assertThat(result.rows()).isEqualTo(1);
        assertThat(result.successes()).isEqualTo(1);
    }

    @Test
    void optimisticLock_allowsExactlyOne() throws InterruptedException {
        LabResult result = runSameSlot("3. optimistic lock", this::optimisticLock, "demo_booking");

        assertThat(result.rows()).isEqualTo(1);
        assertThat(result.successes()).isEqualTo(1);
    }

    // ===================== Thí nghiệm 2: 20 người đặt 20 slot KHÁC NHAU =====================

    @Test
    void differentSlots_uniqueRunsInParallel_butPessimisticQueuesEveryone() throws InterruptedException {
        int threads = 20;
        LabResult unique = runDifferentSlots("1. unique constraint", threads, this::uniqueConstraint, "demo_booking_unique");
        LabResult pessimistic = runDifferentSlots("2. pessimistic lock", threads, this::pessimisticLock, "demo_booking");

        // Cả 2 đều đúng: 20 slot khác nhau -> 20 booking
        assertThat(unique.rows()).isEqualTo(threads);
        assertThat(pessimistic.rows()).isEqualTo(threads);

        // Pessimistic khóa CẢ SÂN nên 20 request phải lần lượt từng cái một:
        // tổng thời gian >= 20 x THINK_TIME, dù chẳng ai tranh slot của ai.
        assertThat(pessimistic.elapsedMs()).isGreaterThanOrEqualTo(threads * THINK_TIME_MS);
    }

    // ===================== Hỗ trợ =====================

    private record LabResult(long rows, long successes, long elapsedMs) {
    }

    private LabResult runSameSlot(String name, Function<LocalTime, Boolean> strategy, String table)
            throws InterruptedException {
        LocalTime slot = LocalTime.of(17, 0);
        return runLab(name, THREADS, i -> slot, strategy, table);
    }

    private LabResult runDifferentSlots(String name, int threads, Function<LocalTime, Boolean> strategy, String table)
            throws InterruptedException {
        return runLab(name, threads, i -> LocalTime.of(6, 0).plusMinutes(30L * i), strategy, table);
    }

    private LabResult runLab(String name, int threads, java.util.function.IntFunction<LocalTime> slotOf,
                             Function<LocalTime, Boolean> strategy, String table) throws InterruptedException {
        long startedAt = System.nanoTime();
        List<Outcome<Boolean>> outcomes = ConcurrencyRunner.run(threads, i -> () -> strategy.apply(slotOf.apply(i)));
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(outcomes).as("không thread nào được ném lỗi bất ngờ").allMatch(Outcome::isSuccess);
        long successes = outcomes.stream().filter(o -> Boolean.TRUE.equals(o.value())).count();
        Long rows = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);

        System.out.printf("[LAB] %-22s threads=%2d  thành công=%2d  số dòng trong DB=%2d  thời gian=%5d ms%n",
                name, threads, successes, rows, elapsedMs);
        return new LabResult(rows, successes, elapsedMs);
    }

    private long countBookings(String table, LocalTime slot) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE court_id = 1 AND slot_start = ?", Long.class, slot);
        return count == null ? 0 : count;
    }

    private static void pause() {
        try {
            Thread.sleep(THINK_TIME_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
