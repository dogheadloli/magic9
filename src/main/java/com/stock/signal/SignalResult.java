package com.stock.signal;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 信号评估结果。
 */
@Data
public class SignalResult {
    private String code;
    private String name;
    private LocalDate tradeDate;
    private SignalType type;
    private int score;
    private int maxScore;
    private boolean strong;
    private List<SignalFactor> factors = new ArrayList<>();
    private Map<String, Object> detail = new LinkedHashMap<>();

    /** 交易计划（仅低9买入信号填充）。 */
    private Double entryPrice;
    private Double stopPrice;
    private Double targetPrice;
    private LocalDate latestExitDate;

    public List<String> getFactorLabels() {
        return factors.stream().map(SignalFactor::getLabel).collect(Collectors.toList());
    }

    public String getHitFactorsCsv() {
        return factors.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
