package com.stock.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 币安现货监控配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "crypto")
public class CryptoProperties {

    /** 总开关：关闭后调度不跑，手动接口仍可用 */
    private boolean enabled = true;

    /** 低9计划按自然日持有（加密 24/7，不再用 A 股交易日） */
    private int holdCalendarDays = 15;

    private final Binance binance = new Binance();
    private final Fetch fetch = new Fetch();
    private final Scan scan = new Scan();

    @Data
    public static class Binance {
        private String baseUrl = "https://api.binance.com";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
    }

    @Data
    public static class Fetch {
        private int historyBarsDaily = 250;
        private int historyBars4h = 360;
        private int liveBars = 5;
    }

    @Data
    public static class Scan {
        private boolean enabled = true;
        private long intervalMs = 300000;
        private double ratePerSecond = 4;
        private int retry = 3;
        private int threadPoolSize = 4;
    }

    public int historyBars(CryptoInterval interval) {
        return interval == CryptoInterval.H4 ? fetch.historyBars4h : fetch.historyBarsDaily;
    }
}
