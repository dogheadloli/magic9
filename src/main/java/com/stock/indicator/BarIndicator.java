package com.stock.indicator;

import lombok.Data;

import java.time.LocalDate;

/**
 * 单根K线的指标快照（用于信号判断与前端绘图）。
 * 指标数据不足时相关字段为 null。
 */
@Data
public class BarIndicator {
    private LocalDate tradeDate;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private Double changePct;

    private int tdBuySetup;
    private int tdSellSetup;
    private TdSignal tdSignal;

    private Double dif;
    private Double dea;
    private Double macd;

    private Double ma5;
    private Double ma10;
    private Double ma20;
    private Double ma60;

    private Double volMa5;
    private Double volMa20;

    private Double bias20;
    private Double bias60;

    private Double atr;
}
