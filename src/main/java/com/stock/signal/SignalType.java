package com.stock.signal;

/**
 * 信号类型。
 */
public enum SignalType {
    /** 低9抄底 */
    BUY_LOW9("低9抄底"),
    /** 高9逃顶 */
    SELL_HIGH9("高9逃顶");

    private final String label;

    SignalType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
