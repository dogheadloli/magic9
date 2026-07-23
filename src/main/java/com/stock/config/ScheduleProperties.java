package com.stock.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "schedule")
public class ScheduleProperties {

    /** 是否启用盘中实时扫描 */
    private boolean intradayEnabled = true;

    /** 盘中扫描间隔（毫秒） */
    private long intradayIntervalMs = 300000;

    /** 交易时段，格式 "HH:mm-HH:mm" */
    private List<String> tradeSessions = new ArrayList<>();

    /** 法定节假日（yyyy-MM-dd），命中则不视为交易日 */
    private List<String> holidays = new ArrayList<>();

    /** 即使非交易时段也强制扫描（调试用） */
    private boolean forceScan = false;

    /** 扫描线程池大小 */
    private int threadPoolSize = 8;

    /** 拉取限流：每秒请求数 */
    private double ratePerSecond = 8;

    /** 单只标的拉取失败重试次数 */
    private int retry = 3;
}
