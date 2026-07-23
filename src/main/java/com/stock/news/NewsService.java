package com.stock.news;

import com.stock.domain.NewsItem;
import com.stock.repository.NewsItemRepository;
import com.stock.repository.StockPoolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 个股新闻服务：抓取 → 去重入库 → AI 情感分析 → 查询。
 */
@Slf4j
@Service
public class NewsService {

    private final NewsProvider newsProvider;
    private final SentimentService sentimentService;
    private final NewsItemRepository repository;
    private final StockPoolRepository poolRepository;

    public NewsService(NewsProvider newsProvider, SentimentService sentimentService,
                       NewsItemRepository repository, StockPoolRepository poolRepository) {
        this.newsProvider = newsProvider;
        this.sentimentService = sentimentService;
        this.repository = repository;
        this.poolRepository = poolRepository;
    }

    /** 抓取并分析该股票最近 limit 条新闻，返回本次新增条数。 */
    @Transactional
    public int refresh(String code, int limit) {
        String name = poolRepository.findByCode(code).map(p -> p.getName()).orElse(null);
        List<NewsArticle> articles = newsProvider.fetchNews(code, name, limit);
        int added = 0;
        for (NewsArticle a : articles) {
            if (a.getUrl() == null || a.getUrl().isEmpty()
                    || repository.existsByCodeAndUrl(code, a.getUrl())) {
                continue;
            }
            NewsItem item = new NewsItem();
            item.setCode(code);
            item.setTitle(a.getTitle());
            item.setSummary(a.getSummary());
            item.setSource(a.getSource());
            item.setUrl(a.getUrl());
            item.setPublishTime(a.getPublishTime());

            SentimentResult r = sentimentService.classify(name, code, a.getTitle(), a.getSummary());
            item.setSentiment(r.getSentiment());
            item.setSentimentScore(r.getScore());
            item.setConfidence(r.getConfidence());
            item.setReason(r.getReason());
            item.setAnalyzed(r.getSentiment() != Sentiment.UNKNOWN);

            repository.save(item);
            added++;
        }
        int reanalyzed = reanalyzeStored(code, name);
        log.info("新闻刷新 code={} 抓取={} 新增={} 补判={} AI={}",
                code, articles.size(), added, reanalyzed, sentimentService.isReady());
        return added;
    }

    /** 对库中已存在但尚未做 AI 判断的新闻补做情感分析（用于开启 AI 后回填存量）。 */
    private int reanalyzeStored(String code, String name) {
        if (!sentimentService.isReady()) {
            return 0;
        }
        List<NewsItem> pending = repository.findTop20ByCodeAndAnalyzedFalseOrderByPublishTimeDesc(code);
        int done = 0;
        for (NewsItem item : pending) {
            SentimentResult r = sentimentService.classify(name, code, item.getTitle(), item.getSummary());
            if (r.getSentiment() == Sentiment.UNKNOWN) {
                continue;
            }
            item.setSentiment(r.getSentiment());
            item.setSentimentScore(r.getScore());
            item.setConfidence(r.getConfidence());
            item.setReason(r.getReason());
            item.setAnalyzed(true);
            repository.save(item);
            done++;
        }
        return done;
    }

    public List<NewsItem> list(String code) {
        return repository.findTop30ByCodeOrderByPublishTimeDesc(code);
    }

    public boolean aiReady() {
        return sentimentService.isReady();
    }
}
