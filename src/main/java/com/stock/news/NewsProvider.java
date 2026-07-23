package com.stock.news;

import java.util.List;

/**
 * 个股新闻数据源（可插拔）。
 */
public interface NewsProvider {

    /**
     * 拉取与该股票相关的最近新闻。
     *
     * @param code  股票代码
     * @param name  股票名称（用于检索，可空）
     * @param limit 条数
     */
    List<NewsArticle> fetchNews(String code, String name, int limit);
}
