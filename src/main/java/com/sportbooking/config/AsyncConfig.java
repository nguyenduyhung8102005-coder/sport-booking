package com.sportbooking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bật @Async và khai báo thread pool RIÊNG cho việc gửi email.
 *
 * Vì sao không để mặc định? Gửi email chậm (vài trăm ms tới vài giây) và có thể treo khi SMTP lỗi.
 * Một pool riêng, giới hạn rõ ràng, đảm bảo email không chiếm thread xử lý việc khác.
 *
 *   core 2 / max 4 thread, hàng đợi 500 email.
 *   Hàng đợi đầy -> BỎ email đó và ghi log, KHÔNG ném lỗi ngược về luồng gọi:
 *   lỗi gửi email không được làm hỏng việc thanh toán / đặt sân đã thành công.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    @Bean(name = MAIL_EXECUTOR)
    public ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Mail queue is full, dropping one email task"));
        // Tắt app: đợi tối đa 10 giây cho các email đang gửi dở
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /** Lỗi trong method @Async trả về void không có ai nhận -> ít nhất phải ghi log. */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, method, params) -> log.error("Async method {} failed", method.getName(), error);
    }
}
