package com.stock.domain;

/**
 * 低9信号交易计划的生命周期状态。
 */
public enum TradeTrackStatus {
    /** 尚未触发退出条件 */
    OPEN,
    /** 回到 MA20 止盈 */
    TP,
    /** 跌破计划止损 */
    SL,
    /** 达到最大持有交易日后按收盘价退出 */
    TIME
}
