package com.stock.analysis;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单只股票的历史逐笔复盘（低9买点，回测优选规则）。
 */
@Data
public class HistoryReport {
    private String code;
    private String name;
    private String rule = "低9买入 · ATR×1.5止损 · 回MA20止盈 · 最多持有15个交易日";

    private List<TradeDetail> trades = new ArrayList<>();

    private int count;
    private int wins;
    private double winRatePct;
    private double avgReturnPct;
    private double avgWinPct;
    private double avgLossPct;
    private double profitFactor;
    private double avgHoldDays;
    private double totalReturnPct;
    /** 仍在持有中（无足够后续K线了结）的笔数 */
    private int openCount;
}
