package com.stock.analysis;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 单条低9信号的复盘结果（数据库信号 + 回测优选规则模拟的结果）。
 */
@Data
public class ReviewItem {
    private Long id;
    private String code;
    private String name;
    private LocalDate tradeDate;
    private int score;
    private int maxScore;
    private boolean strong;
    private List<String> factors;

    private Double entryPrice;
    private Double stopPrice;
    private Double targetPrice;
    private LocalDate latestExitDate;

    private LocalDate exitDate;
    private Double exitPrice;
    /** TP止盈 / SL止损 / TIME到期 / OPEN持有中 / NA数据缺失 */
    private String reason;
    private Double returnPct;
    private Integer holdDays;
}
