package com.stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 神奇九转批量选股监控系统 启动类。
 */
@EnableScheduling
@SpringBootApplication
public class StockMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockMonitorApplication.class, args);
    }
}
