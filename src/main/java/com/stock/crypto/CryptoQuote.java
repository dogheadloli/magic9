package com.stock.crypto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 币安 24h 行情快照。
 */
@Data
public class CryptoQuote {
    private String symbol;
    private String name;
    private BigDecimal price;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal volume;
    private BigDecimal amount;
    private BigDecimal changePct;
}
