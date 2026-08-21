package com.stock.crypto;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CryptoConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService cryptoScanExecutor(CryptoProperties props) {
        int size = Math.max(1, props.getScan().getThreadPoolSize());
        return Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, "crypto-scan");
            t.setDaemon(true);
            return t;
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    @Bean
    public RateLimiter cryptoRateLimiter(CryptoProperties props) {
        double rate = props.getScan().getRatePerSecond() > 0 ? props.getScan().getRatePerSecond() : 4;
        return RateLimiter.create(rate);
    }
}
