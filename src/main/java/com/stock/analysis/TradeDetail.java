package com.stock.analysis;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 单笔（低9买点）历史复盘明细。
 */
@Data
public class TradeDetail {
    private LocalDate entryDate;
    private double entryPrice;
    private Double stopPrice;
    private Double targetPrice;

    private LocalDate exitDate;
    private Double exitPrice;
    /** 退出原因：TP 止盈 / SL 止损 / TIME 到期 / OPEN 持有中 */
    private String reason;

    private Double returnPct;
    private Integer holdDays;

    private int score;
    private List<String> factors;
}
