package com.stock.news;

import lombok.Data;

import java.time.LocalDateTime;

/** 新闻原始条目（来自数据源，未经情感分析）。 */
@Data
public class NewsArticle {
    private String title;
    private String summary;
    private String source;
    private String url;
    private LocalDateTime publishTime;
}
