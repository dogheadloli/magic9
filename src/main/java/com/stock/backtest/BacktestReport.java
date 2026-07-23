package com.stock.backtest;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 回测汇总：信号样本量 + 各参数组合的统计结果（已排序）。
 */
@Data
public class BacktestReport {
    private int stocks;
    private int totalLow9;
    private int entriesScore1;
    private int entriesScore2;
    private int entriesScore3;
    private double stopBuffer;
    private int atrPeriod;
    private String entryRule;
    private List<ComboResult> combos = new ArrayList<>();
    private ComboResult best;
}
