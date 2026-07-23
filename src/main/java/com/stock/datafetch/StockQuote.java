package com.stock.datafetch;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 实时行情快照。
 */
@Data
public class StockQuote {
    private String code;
    private String name;
    /** 最新价 */
    private BigDecimal price;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    /** 昨收 */
    private BigDecimal preClose;
    /** 成交量（手） */
    private Long volume;
    /** 成交额（元） */
    private BigDecimal amount;
    /** 涨跌幅（%） */
    private BigDecimal changePct;
}
