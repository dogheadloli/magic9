package com.stock.datafetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.domain.Market;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 腾讯财经免费接口数据适配器（ifzq.gtimg.cn）。
 * <p>当前环境下东方财富 K 线域名(push2his)不可达，故以腾讯为主数据源（@Primary）。
 * 接口返回前复权日K，中文以 unicode 转义，无编码问题。
 */
@Slf4j
@Primary
@Component
public class TencentProvider implements MarketDataProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${market.tencent.kline-url:https://web.ifzq.gtimg.cn/appstock/app/fqkline/get}")
    private String klineUrl;

    @Value("${market.tencent.quote-batch-url:https://qt.gtimg.cn/q=}")
    private String quoteBatchUrl;

    public TencentProvider(RestTemplate marketRestTemplate, ObjectMapper objectMapper) {
        this.restTemplate = marketRestTemplate;
        this.objectMapper = objectMapper;
    }

    private String symbol(String code, Market market) {
        Market m = market != null ? market : Market.inferByCode(code);
        String prefix = (m == Market.SH) ? "sh" : (m == Market.BJ) ? "bj" : "sz";
        return prefix + code;
    }

    @Override
    public List<KlineBar> fetchDailyKline(String code, Market market, int limit) {
        String sym = symbol(code, market);
        String url = UriComponentsBuilder.fromHttpUrl(klineUrl)
                .queryParam("param", sym + ",day,,," + limit + ",qfq")
                .build().toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode stock = objectMapper.readTree(body).path("data").path(sym);
            JsonNode kl = stock.path("qfqday");
            if (!kl.isArray() || kl.size() == 0) {
                kl = stock.path("day");
            }
            if (!kl.isArray() || kl.size() == 0) {
                log.warn("腾讯未返回K线数据, code={}", code);
                return Collections.emptyList();
            }
            List<KlineBar> result = new ArrayList<>(kl.size());
            BigDecimal prevClose = null;
            for (JsonNode row : kl) {
                // [日期, 开, 收, 高, 低, 成交量(手)]
                KlineBar bar = new KlineBar();
                bar.setTradeDate(LocalDate.parse(row.get(0).asText()));
                bar.setOpen(decimal(row, 1));
                bar.setClose(decimal(row, 2));
                bar.setHigh(decimal(row, 3));
                bar.setLow(decimal(row, 4));
                bar.setVolume(longVal(row, 5));
                if (prevClose != null && prevClose.signum() != 0 && bar.getClose() != null) {
                    bar.setChangePct(bar.getClose().subtract(prevClose)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(prevClose, 3, RoundingMode.HALF_UP));
                }
                prevClose = bar.getClose();
                result.add(bar);
            }
            return result;
        } catch (Exception e) {
            log.error("拉取腾讯K线失败, code={}, err={}", code, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public StockQuote fetchQuote(String code, Market market) {
        String sym = symbol(code, market);
        String url = UriComponentsBuilder.fromHttpUrl(klineUrl)
                .queryParam("param", sym + ",day,,,1,qfq")
                .build().toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode qt = objectMapper.readTree(body).path("data").path(sym).path("qt").path(sym);
            if (!qt.isArray() || qt.size() < 35) {
                return null;
            }
            // 腾讯 qt 字段：1名称 2代码 3现价 4昨收 5今开 6成交量(手) 32涨跌幅 33最高 34最低
            StockQuote q = new StockQuote();
            q.setName(qt.get(1).asText());
            q.setCode(qt.get(2).asText());
            q.setPrice(toDecimal(qt.get(3).asText()));
            q.setPreClose(toDecimal(qt.get(4).asText()));
            q.setOpen(toDecimal(qt.get(5).asText()));
            q.setVolume(toLong(qt.get(6).asText()));
            q.setChangePct(toDecimal(qt.get(32).asText()));
            q.setHigh(toDecimal(qt.get(33).asText()));
            q.setLow(toDecimal(qt.get(34).asText()));
            return q;
        } catch (Exception e) {
            log.error("拉取腾讯行情失败, code={}, err={}", code, e.getMessage());
            return null;
        }
    }

    /**
     * 批量行情：调用 qt.gtimg.cn 一次查询多只（返回 GBK 文本，按 ~ 分隔）。
     * 字段索引与单只 qt 一致：1名称 2代码 3现价 4昨收 5今开 6成交量 32涨跌幅 33最高 34最低。
     */
    @Override
    public List<StockQuote> fetchQuotes(List<String> codes) {
        List<StockQuote> result = new ArrayList<>();
        if (codes == null || codes.isEmpty()) {
            return result;
        }
        List<String> syms = new ArrayList<>(codes.size());
        for (String code : codes) {
            syms.add(symbol(code, Market.inferByCode(code)));
        }
        String url = quoteBatchUrl + String.join(",", syms);
        try {
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) {
                return result;
            }
            String body = new String(bytes, java.nio.charset.Charset.forName("GBK"));
            for (String line : body.split("\n")) {
                int q1 = line.indexOf('"');
                int q2 = line.lastIndexOf('"');
                if (q1 < 0 || q2 <= q1) {
                    continue;
                }
                String[] f = line.substring(q1 + 1, q2).split("~");
                if (f.length < 35) {
                    continue;
                }
                StockQuote q = new StockQuote();
                q.setName(f[1]);
                q.setCode(f[2]);
                q.setPrice(toDecimal(f[3]));
                q.setPreClose(toDecimal(f[4]));
                q.setOpen(toDecimal(f[5]));
                q.setVolume(toLong(f[6]));
                q.setChangePct(toDecimal(f[32]));
                q.setHigh(toDecimal(f[33]));
                q.setLow(toDecimal(f[34]));
                result.add(q);
            }
        } catch (Exception e) {
            log.error("批量拉取腾讯行情失败, count={}, err={}", codes.size(), e.getMessage());
        }
        return result;
    }

    private static BigDecimal decimal(JsonNode row, int idx) {
        if (row.size() <= idx) {
            return null;
        }
        return toDecimal(row.get(idx).asText());
    }

    private static Long longVal(JsonNode row, int idx) {
        if (row.size() <= idx) {
            return null;
        }
        return toLong(row.get(idx).asText());
    }

    private static BigDecimal toDecimal(String s) {
        if (s == null || s.isEmpty() || "-".equals(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long toLong(String s) {
        if (s == null || s.isEmpty() || "-".equals(s)) {
            return null;
        }
        try {
            return (long) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
