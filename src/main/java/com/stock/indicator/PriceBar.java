package com.stock.indicator;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 通用 OHLC 输入，供股票日K与加密货币多周期共用指标计算。
 */
@Data
public class PriceBar {
    private LocalDate tradeDate;
    private LocalDateTime openTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    private Double changePct;
}
