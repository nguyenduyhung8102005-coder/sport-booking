package com.sportbooking.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

/**
 * Chạy N tác vụ THẬT SỰ cùng lúc để giả lập N người bấm "Đặt sân" cùng một khoảnh khắc.
 *
 * Nếu chỉ submit N tác vụ vào thread pool, thread đầu tiên đã chạy xong trước khi thread cuối được tạo.
 * Dùng 2 CountDownLatch như vạch xuất phát của cuộc đua:
 *   - ready: mỗi thread báo "tôi đã vào vạch"; luồng chính đợi đủ N thread.
 *   - start: luồng chính "bắn súng" (countDown) -> N thread cùng lao vào một lúc.
 */
public final class ConcurrencyRunner {

    private ConcurrencyRunner() {
    }

    /** Kết quả của 1 thread: hoặc giá trị trả về, hoặc exception đã ném ra. */
    public record Outcome<T>(T value, Throwable error) {
        public boolean isSuccess() {
            return error == null;
        }
    }

    public static <T> List<Outcome<T>> run(int threads, IntFunction<Callable<T>> taskFactory)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<T> task = taskFactory.apply(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();          // chờ hiệu lệnh xuất phát
                    return task.call();
                }));
            }
            ready.await();                  // đợi đủ N thread vào vạch
            start.countDown();              // xuất phát!

            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(new Outcome<>(future.get(60, TimeUnit.SECONDS), null));
                } catch (ExecutionException e) {
                    outcomes.add(new Outcome<>(null, e.getCause()));
                } catch (TimeoutException e) {
                    throw new IllegalStateException("Tác vụ chạy quá 60 giây (nghi deadlock?)", e);
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
