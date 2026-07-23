package com.stock.news;

/**
 * 新闻情感倾向。
 */
public enum Sentiment {
    BULLISH("利好"),
    BEARISH("利空"),
    NEUTRAL("中性"),
    UNKNOWN("未分析");

    private final String label;

    Sentiment(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Sentiment fromCn(String s) {
        if (s == null) {
            return UNKNOWN;
        }
        if (s.contains("利好") || s.equalsIgnoreCase("bullish") || s.contains("正面")) {
            return BULLISH;
        }
        if (s.contains("利空") || s.equalsIgnoreCase("bearish") || s.contains("负面")) {
            return BEARISH;
        }
        if (s.contains("中性") || s.equalsIgnoreCase("neutral")) {
            return NEUTRAL;
        }
        return UNKNOWN;
    }
}
