package com.stock.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 策略参数（可在 application.yml 的 strategy.* 配置，支持调参）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "strategy")
public class StrategyProperties {

    /** TD 九转完成计数 */
    private int tdSetupLength = 9;

    private final Macd macd = new Macd();
    private final Divergence divergence = new Divergence();
    private final Volume volume = new Volume();
    private final MaSupport maSupport = new MaSupport();
    private final Bias bias = new Bias();
    private final TradePlan tradePlan = new TradePlan();

    /** 组合判定模式：SCORE 加权打分 / STRICT 严格(四要素全中) */
    private String comboMode = "SCORE";
    /** 打分阈值：除必备项外，命中加分项 >= 该值即预警 */
    private int scoreThreshold = 2;

    @Data
    public static class Macd {
        private int fast = 12;
        private int slow = 26;
        private int signal = 9;
    }

    @Data
    public static class Divergence {
        /** 背离检测窗口（根） */
        private int window = 60;
        /** 摆动极值的左右确认根数 */
        private int swingK = 2;
    }

    @Data
    public static class Volume {
        /** 缩量比较的近/前段天数 */
        private int shrinkDays = 5;
        /** 量能均线周期 */
        private int maPeriod = 20;
        /** 放量倍数阈值（相对量能均线） */
        private double surgeRatio = 1.8;
        /** 滞涨判定：当日涨幅 <= 该值(%) */
        private double stallChangePct = 1.0;
    }

    @Data
    public static class MaSupport {
        /** 关注的均线（踩均线支撑/远离均线） */
        private int[] periods = {20, 60};
        /** 贴合容差（2%） */
        private double tolerance = 0.02;
    }

    @Data
    public static class Bias {
        /** MA20 远离阈值(%)：BIAS20 >= 该值视为远离 */
        private double ma20Far = 15.0;
        /** MA60 远离阈值(%) */
        private double ma60Far = 25.0;
    }

    /**
     * 低9买入信号的交易计划（回测优选：ATR×1.5 止损 / 回 MA20 止盈 / 最多持有 15 个交易日）。
     */
    @Data
    public static class TradePlan {
        /** ATR 计算周期 */
        private int atrPeriod = 14;
        /** 止损 = 进场价 - atrMult × ATR */
        private double atrMult = 1.5;
        /** 最晚持有的交易日数（到期了结） */
        private int holdTradingDays = 15;
        /** MA20 不可用或不在上方时的兜底止盈百分比 */
        private double fallbackTpPct = 5.0;
        /** ATR 不可用时的兜底止损百分比 */
        private double fallbackSlPct = 5.0;
        /** 实时价连续低于止损价达到该分钟数后触发软止损 */
        private int softStopDurationMinutes = 60;
        /** 两次监测超过该间隔视为不连续，软止损重新计时 */
        private int softStopMaxCheckGapMinutes = 10;
        /** 跌至止损价下方该百分比时立即触发硬止损 */
        private double hardStopBelowPct = 2.0;
    }
}
