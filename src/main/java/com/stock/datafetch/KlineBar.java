package com.stock.datafetch;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单根日K线数据（数据源返回的中间结构）。
 */
@Data
public class KlineBar {
    private LocalDate tradeDate;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;
    private BigDecimal amount;
    private BigDecimal changePct;
}
