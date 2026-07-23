package com.stock.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 东方财富个股资讯检索（search-api-web，JSONP）。
 */
@Slf4j
@Component
public class EastMoneyNewsProvider implements NewsProvider {

    private static final String BASE = "https://search-api-web.eastmoney.com/search/jsonp";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EastMoneyNewsProvider(RestTemplate marketRestTemplate, ObjectMapper objectMapper) {
        this.restTemplate = marketRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<NewsArticle> fetchNews(String code, String name, int limit) {
        List<NewsArticle> result = new ArrayList<>();
        String keyword = (name != null && !name.isEmpty()) ? name : code;
        String param = "{\"uid\":\"\",\"keyword\":\"" + keyword
                + "\",\"type\":[\"cmsArticleWebOld\"],\"client\":\"web\",\"clientType\":\"web\",\"clientVersion\":\"curr\","
                + "\"param\":{\"cmsArticleWebOld\":{\"searchScope\":\"default\",\"sort\":\"default\",\"pageIndex\":1,\"pageSize\":"
                + limit + ",\"preTag\":\"\",\"postTag\":\"\"}}}";
        try {
            String enc = URLEncoder.encode(param, "UTF-8");
            // 关键：用 URI 传入，避免 RestTemplate 把已编码的 %7B/%7D 当作模板变量再次编码导致 400。
            URI uri = URI.create(BASE + "?cb=jsonp&param=" + enc);
            String body = restTemplate.getForObject(uri, String.class);
            if (body == null) {
                return result;
            }
            int s = body.indexOf('(');
            int e = body.lastIndexOf(')');
            if (s < 0 || e <= s) {
                return result;
            }
            JsonNode root = objectMapper.readTree(body.substring(s + 1, e));
            JsonNode arr = root.path("result").path("cmsArticleWebOld");
            if (!arr.isArray()) {
                return result;
            }
            for (JsonNode n : arr) {
                NewsArticle a = new NewsArticle();
                a.setTitle(clean(n.path("title").asText("")));
                a.setSummary(clean(n.path("content").asText("")));
                a.setSource(n.path("mediaName").asText(""));
                a.setUrl(n.path("url").asText(""));
                a.setPublishTime(parseTime(n.path("date").asText("")));
                if (a.getUrl() != null && !a.getUrl().isEmpty()) {
                    result.add(a);
                }
            }
        } catch (Exception ex) {
            log.error("拉取东财新闻失败 code={} err={}", code, ex.getMessage());
        }
        return result;
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
    }

    private static LocalDateTime parseTime(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.trim(), FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
