package com.stock.crypto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 币安 K 线中间结构。
 */
@Data
public class CryptoKlineBar {
    private LocalDateTime openTime;
    private LocalDate tradeDate;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    private BigDecimal amount;
    private BigDecimal changePct;
}
