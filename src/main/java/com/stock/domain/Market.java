package com.stock.domain;

/**
 * 交易市场。
 */
public enum Market {
    /** 上交所 */
    SH,
    /** 深交所 */
    SZ,
    /** 北交所 */
    BJ;

    /**
     * 根据 6 位股票代码推断所属市场。
     */
    public static Market inferByCode(String code) {
        if (code == null || code.isEmpty()) {
            return SZ;
        }
        char c0 = code.charAt(0);
        // 北交所：8/4 开头，以及 92 开头
        if (c0 == '8' || c0 == '4' || code.startsWith("92")) {
            return BJ;
        }
        // 上交所：6 开头（股票），5 开头（ETF/基金），688 科创板，11 开头（沪可转债），90 开头
        if (c0 == '6' || c0 == '5' || code.startsWith("11") || code.startsWith("90")) {
            return SH;
        }
        // 其余归深交所：0/3 股票，15/16 深ETF，12 深可转债
        return SZ;
    }
}
