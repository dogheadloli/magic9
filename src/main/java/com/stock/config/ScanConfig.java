package com.stock.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扫描并发与限流 Bean。
 */
@Configuration
public class ScanConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService scanExecutor(ScheduleProperties props) {
        int size = Math.max(1, props.getThreadPoolSize());
        return Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, "scan-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    @Bean
    public RateLimiter scanRateLimiter(ScheduleProperties props) {
        double rate = props.getRatePerSecond() > 0 ? props.getRatePerSecond() : 8;
        return RateLimiter.create(rate);
    }
}
