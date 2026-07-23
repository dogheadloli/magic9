package com.stock.backtest;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MA20 全仓止盈与 MA20 半仓+ATR移动止盈的对比报告。
 */
@Data
public class ExitStrategyComparison {
    private String fullRule;
    private String trailingRule;
    private int totalSignals;
    private int dataMissing;
    private StrategyStats fullMa20 = new StrategyStats();
    private StrategyStats halfTrailing = new StrategyStats();
    private int matchedClosed;
    private int trailingBetter;
    private Double avgReturnDeltaPct;
    private List<TradeComparison> trades = new ArrayList<>();

    @Data
    public static class StrategyStats {
        private int closed;
        private int open;
        private int wins;
        private Double winRatePct;
        private Double avgReturnPct;
        private Double medianReturnPct;
        private Double profitFactor;
        private Double cumulativeReturnPct;
        private Double maxDrawdownPct;
    }

    @Data
    public static class TradeComparison {
        private Long signalId;
        private String code;
        private String name;
        private LocalDate signalDate;
        private Double entryPrice;
        private Double stopPrice;
        private Double initialRiskPct;
        private int score;
        private int maxScore;
        private boolean strong;
        private List<String> factors;
        private List<String> factorCodes;
        private Result fullMa20;
        private Result halfTrailing;
        private Double returnDeltaPct;
    }

    @Data
    public static class Result {
        private String status;
        private LocalDate exitDate;
        private Double exitPrice;
        private Double returnPct;
        private Integer holdDays;
        private Double firstHalfExitPrice;
        private LocalDate firstHalfExitDate;
    }
}
