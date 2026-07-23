package com.stock.news;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 情感分析结果。 */
@Data
@AllArgsConstructor
public class SentimentResult {
    private Sentiment sentiment;
    private Double score;
    private Double confidence;
    private String reason;

    public static SentimentResult unknown(String reason) {
        return new SentimentResult(Sentiment.UNKNOWN, null, null, reason);
    }
}
