package com.stock.diagnosis;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 诊股对外结果：规则分 + AI 解读 + 关键事实快照。
 */
@Data
public class DiagnosisView {

    private String code;
    private String name;
    private LocalDate asOf;
    private Double price;

    /** 是否真正调用了 AI（false 表示规则降级结果） */
    private boolean aiUsed;
    /** 规则综合分（确定性，0~100），作为锚点与降级展示 */
    private Integer ruleScore;

    /** 最终评分与立场（AI 优先，否则取规则） */
    private Integer score;
    private String stance;          // 偏多 / 偏空 / 中性 / 观望

    private String trend;           // 趋势解读
    private String momentum;        // 动能解读
    private String signal;          // 信号解读
    private String sentiment;       // 舆情解读
    private List<String> risks = new ArrayList<>();

    private Action action = new Action();
    private String summary;         // 一句话结论

    /** 关键事实快照（供前端透明展示） */
    private Facts facts = new Facts();

    private String disclaimer = "本诊断由量化规则与AI模型生成，仅供参考，不构成投资建议。";
    private String generatedAt;

    @Data
    public static class Action {
        private String direction;   // 买入 / 持有 / 减仓 / 观望
        private String entry;        // 建议区间（文字）
        private Double stop;
        private Double target;
        private String position;     // 仓位建议（轻/中/重仓）
    }

    @Data
    public static class Facts {
        private Double changePct;
        private String maTrend;      // 多头排列 / 空头排列 / 均线纠缠
        private String priceVsMa20;  // 站上MA20 / 跌破MA20
        private String macdState;    // 金叉区 / 死叉区
        private String volState;     // 放量 / 缩量 / 平量
        private Double bias20;
        private String signalType;   // 低9买入 / 高9卖出 / 无
        private LocalDate signalDate;
        private boolean signalFresh;
        private String signalScore;  // x/y
        private Integer histSamples;
        private Double histWinRatePct;
        private Double histProfitFactor;
        private int newsBullish;
        private int newsBearish;
        private int newsNeutral;
    }
}
