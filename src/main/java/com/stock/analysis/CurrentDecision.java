package com.stock.analysis;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 当下决策：当前/最近一次信号 + 交易计划 + 现价距离。
 */
@Data
public class CurrentDecision {
    private String code;
    private String name;
    private LocalDate asOf;
    private Double price;

    private boolean hasSignal;
    /** true=最新一根即触发；false=最近的一次历史信号 */
    private boolean fresh;
    private String signalType;
    private LocalDate signalDate;
    private int score;
    private int maxScore;
    private List<String> factors;

    /** 交易计划（仅低9买入） */
    private Double entryPrice;
    private Double stopPrice;
    private Double targetPrice;
    private LocalDate latestExitDate;

    private Double distToStopPct;
    private Double distToTargetPct;
    private boolean brokeStop;
    private boolean expired;

    private String advice;
}
