package com.stock.backtest;

import lombok.Data;

/**
 * 单个「止损×止盈×持有×信号强度」组合的回测统计结果。
 */
@Data
public class ComboResult {
    private int minScore;
    private String sl;
    private String tp;
    private int holdBars;

    private int trades;
    private int wins;
    private double winRatePct;
    private double avgReturnPct;
    private double avgWinPct;
    private double avgLossPct;
    private double profitFactor;
    private double expectancyPct;
    private double maxDrawdownPct;

    public String label() {
        return "score>=" + minScore + " | SL=" + sl + " | TP=" + tp + " | hold=" + holdBars;
    }
}
