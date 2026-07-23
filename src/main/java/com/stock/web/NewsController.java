package com.stock.web;

import com.stock.domain.NewsItem;
import com.stock.news.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个股新闻 + AI 情感接口。
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    /** 查询已存新闻；refresh=true 时先抓取并分析。 */
    @GetMapping("/{code}")
    public Map<String, Object> list(@PathVariable String code,
                                    @RequestParam(defaultValue = "false") boolean refresh,
                                    @RequestParam(defaultValue = "10") int limit) {
        int added = 0;
        if (refresh) {
            added = newsService.refresh(code, limit);
        }
        List<NewsItem> items = newsService.list(code);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiReady", newsService.aiReady());
        result.put("added", added);
        result.put("items", items);
        return result;
    }

    @PostMapping("/{code}/refresh")
    public Map<String, Object> refresh(@PathVariable String code,
                                       @RequestParam(defaultValue = "10") int limit) {
        int added = newsService.refresh(code, limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiReady", newsService.aiReady());
        result.put("added", added);
        result.put("items", newsService.list(code));
        return result;
    }
}
