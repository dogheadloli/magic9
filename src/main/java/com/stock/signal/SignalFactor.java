package com.stock.signal;

/**
 * 组合命中要素。
 */
public enum SignalFactor {
    TD_LOW9("低9完成"),
    MACD_BULL_DIV("MACD底背离"),
    VOL_SHRINK("持续缩量"),
    MA_SUPPORT("回踩均线支撑"),

    TD_HIGH9("高9完成"),
    MACD_BEAR_DIV("MACD顶背离"),
    VOL_SURGE_STALL("放量滞涨"),
    MA_FAR("股价远离均线");

    private final String label;

    SignalFactor(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
