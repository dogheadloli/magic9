package com.stock.journal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 记账总览：逐笔 + 汇总。 */
@Data
public class JournalView {
    private List<TradeView> trades = new ArrayList<>();

    private int openCount;
    private int closedCount;
    /** 持仓成本（仅持仓中） */
    private double cost;
    /** 持仓市值（仅持仓中） */
    private double marketValue;
    /** 浮动盈亏（仅持仓中） */
    private double unrealizedPnl;
    /** 已实现盈亏（仅已平仓） */
    private double realizedPnl;
    private double totalPnl;
    /** 已平仓胜率 */
    private double winRatePct;
}
