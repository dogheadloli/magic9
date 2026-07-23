package com.stock.datafetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.domain.Market;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * 东方财富免费接口数据适配器。
 */
@Slf4j
@Component
public class EastMoneyProvider implements MarketDataProvider {

    private static final String KLINE_FIELDS1 = "f1,f2,f3,f4,f5,f6";
    private static final String KLINE_FIELDS2 = "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61";
    private static final String QUOTE_FIELDS = "f43,f44,f45,f46,f47,f48,f57,f58,f59,f60,f169,f170";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${market.eastmoney.kline-url}")
    private String klineUrl;

    @Value("${market.eastmoney.quote-url}")
    private String quoteUrl;

    public EastMoneyProvider(RestTemplate marketRestTemplate, ObjectMapper objectMapper) {
        this.restTemplate = marketRestTemplate;
        this.objectMapper = objectMapper;
    }

    private String secid(String code, Market market) {
        Market m = market != null ? market : Market.inferByCode(code);
        String prefix = (m == Market.SH) ? "1" : "0";
        return prefix + "." + code;
    }

    @Override
    public List<KlineBar> fetchDailyKline(String code, Market market, int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(klineUrl)
                .queryParam("secid", secid(code, market))
                .queryParam("fields1", KLINE_FIELDS1)
                .queryParam("fields2", KLINE_FIELDS2)
                .queryParam("klt", 101)   // 日线
                .queryParam("fqt", 1)     // 前复权
                .queryParam("beg", 0)
                .queryParam("end", 20500101)
                .queryParam("lmt", limit)
                .build().toUriString();

        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode klines = root.path("data").path("klines");
            if (!klines.isArray() || klines.size() == 0) {
                log.warn("东方财富未返回K线数据, code={}", code);
                return Collections.emptyList();
            }
            List<KlineBar> result = new ArrayList<>(klines.size());
            for (JsonNode node : klines) {
                String[] f = node.asText().split(",");
                // f51 日期, f52 开, f53 收, f54 高, f55 低, f56 量(手), f57 额, f58 振幅, f59 涨跌幅, f60 涨跌额, f61 换手
                KlineBar bar = new KlineBar();
                bar.setTradeDate(LocalDate.parse(f[0]));
                bar.setOpen(parseDecimal(f[1]));
                bar.setClose(parseDecimal(f[2]));
                bar.setHigh(parseDecimal(f[3]));
                bar.setLow(parseDecimal(f[4]));
                bar.setVolume(parseLong(f[5]));
                bar.setAmount(parseDecimal(f[6]));
                bar.setChangePct(parseDecimal(f[8]));
                result.add(bar);
            }
            return result;
        } catch (Exception e) {
            log.error("拉取东方财富K线失败, code={}, err={}", code, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public StockQuote fetchQuote(String code, Market market) {
        String url = UriComponentsBuilder.fromHttpUrl(quoteUrl)
                .queryParam("secid", secid(code, market))
                .queryParam("fields", QUOTE_FIELDS)
                .build().toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonNode data = objectMapper.readTree(body).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            int decimals = data.path("f59").isInt() ? data.path("f59").asInt() : 2;
            BigDecimal scale = BigDecimal.TEN.pow(decimals);

            StockQuote q = new StockQuote();
            q.setCode(text(data, "f57", code));
            q.setName(text(data, "f58", null));
            q.setPrice(scaledPrice(data, "f43", scale));
            q.setHigh(scaledPrice(data, "f44", scale));
            q.setLow(scaledPrice(data, "f45", scale));
            q.setOpen(scaledPrice(data, "f46", scale));
            q.setPreClose(scaledPrice(data, "f60", scale));
            q.setVolume(longVal(data, "f47"));
            q.setAmount(decimalVal(data, "f48"));
            // 涨跌幅 f170 为百分比 * 100
            BigDecimal pct = decimalVal(data, "f170");
            q.setChangePct(pct == null ? null : pct.divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP));
            return q;
        } catch (Exception e) {
            log.error("拉取东方财富行情失败, code={}, err={}", code, e.getMessage());
            return null;
        }
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isEmpty() || "-".equals(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String s) {
        if (s == null || s.isEmpty() || "-".equals(s)) {
            return null;
        }
        try {
            return (long) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field, String def) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? def : v.asText(def);
    }

    private static BigDecimal scaledPrice(JsonNode node, String field, BigDecimal scale) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(v.asDouble()).divide(scale, scale.precision() + 2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static Long longVal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull() || !v.isNumber()) ? null : v.asLong();
    }

    private static BigDecimal decimalVal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull() || !v.isNumber()) ? null : BigDecimal.valueOf(v.asDouble());
    }
}
