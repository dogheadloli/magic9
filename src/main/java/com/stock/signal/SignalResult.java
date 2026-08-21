package com.stock.signal;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    /** 资产类别，默认股票；加密货币为「币安现货」等 */
    private String assetKind = "股票";
    /** 周期，如 D1 / H4；股票日K可为空 */
    private String interval;
    private LocalDate tradeDate;
    private LocalDateTime barTime;
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
