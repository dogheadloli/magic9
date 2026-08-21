package com.stock.indicator;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单根K线的指标快照（用于信号判断与前端绘图）。
 * 指标数据不足时相关字段为 null。
 */
@Data
public class BarIndicator {
    private LocalDate tradeDate;
    /** 开盘时间（加密 4H 等日内周期使用；股票日K可为空） */
    private LocalDateTime openTime;
    /** UTC 开盘毫秒时间戳，供前端绘图 */
    private Long openTimeMs;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
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
