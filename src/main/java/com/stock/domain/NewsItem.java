package com.stock.domain;

import com.stock.news.Sentiment;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 个股新闻 + AI 情感判断结果。
 */
@Data
@Entity
@Table(name = "news_item")
public class NewsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(length = 512)
    private String title;

    @Lob
    @Column(name = "summary")
    private String summary;

    @Column(length = 64)
    private String source;

    @Column(length = 512)
    private String url;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private Sentiment sentiment = Sentiment.UNKNOWN;

    /** -1(极空) ~ +1(极好) */
    @Column(name = "sentiment_score")
    private Double sentimentScore;

    private Double confidence;

    @Column(length = 256)
    private String reason;

    private boolean analyzed = false;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void onCreate() {
        this.createTime = LocalDateTime.now();
    }
}
