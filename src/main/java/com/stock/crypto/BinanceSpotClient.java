package com.stock.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 币安现货公开 REST（K 线 / 24h ticker / 交易对校验）。
 */
@Slf4j
@Component
public class BinanceSpotClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CryptoProperties props;

    public BinanceSpotClient(RestTemplate marketRestTemplate, ObjectMapper objectMapper,
                             CryptoProperties props) {
        this.restTemplate = marketRestTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    public BinanceSymbolInfo resolveSpot(String symbol) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/api/v3/exchangeInfo")
                .queryParam("symbol", symbol)
                .build(true)
                .toUri();
        JsonNode root = getJson(uri, "校验交易对 " + symbol);
        JsonNode symbols = root.path("symbols");
        if (!symbols.isArray() || symbols.size() == 0) {
            throw new IllegalArgumentException("币安现货不存在该交易对: " + symbol);
        }
        JsonNode s = symbols.get(0);
        boolean spot = s.path("isSpotTradingAllowed").asBoolean(false);
        String status = text(s, "status");
        if (!spot) {
            throw new IllegalArgumentException(symbol + " 不是币安现货交易对");
        }
        if (!"TRADING".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException(symbol + " 当前不可交易，状态=" + status);
        }
        BinanceSymbolInfo info = new BinanceSymbolInfo();
        info.setSymbol(text(s, "symbol"));
        info.setBaseAsset(text(s, "baseAsset"));
        info.setQuoteAsset(text(s, "quoteAsset"));
        info.setStatus(status);
        return info;
    }

    public List<CryptoKlineBar> fetchKlines(String symbol, CryptoInterval interval, int limit) {
        int n = Math.max(1, Math.min(limit, 1000));
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/api/v3/klines")
                .queryParam("symbol", symbol)
                .queryParam("interval", interval.getBinance())
                .queryParam("limit", n)
                .build(true)
                .toUri();
        String body = getText(uri, "K线 " + symbol + " " + interval.getBinance());
        try {
            return parseKlines(objectMapper.readTree(body));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析币安K线失败: " + e.getMessage(), e);
        }
    }

    public CryptoQuote fetchQuote(String symbol) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/api/v3/ticker/24hr")
                .queryParam("symbol", symbol)
                .build(true)
                .toUri();
        JsonNode n = getJson(uri, "行情 " + symbol);
        CryptoQuote q = new CryptoQuote();
        q.setSymbol(text(n, "symbol"));
        q.setPrice(decimal(n, "lastPrice"));
        q.setOpen(decimal(n, "openPrice"));
        q.setHigh(decimal(n, "highPrice"));
        q.setLow(decimal(n, "lowPrice"));
        q.setVolume(decimal(n, "volume"));
        q.setAmount(decimal(n, "quoteVolume"));
        q.setChangePct(decimal(n, "priceChangePercent"));
        return q;
    }

    static List<CryptoKlineBar> parseKlines(JsonNode root) {
        if (root == null || !root.isArray()) {
            throw new IllegalStateException("币安K线返回不是数组");
        }
        List<CryptoKlineBar> out = new ArrayList<CryptoKlineBar>();
        BigDecimal prevClose = null;
        for (JsonNode row : root) {
            if (!row.isArray() || row.size() < 8) {
                continue;
            }
            long openMs = row.get(0).asLong();
            LocalDateTime openTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(openMs), ZoneOffset.UTC);
            CryptoKlineBar bar = new CryptoKlineBar();
            bar.setOpenTime(openTime);
            bar.setTradeDate(openTime.toLocalDate());
            bar.setOpen(decimal(row.get(1)));
            bar.setHigh(decimal(row.get(2)));
            bar.setLow(decimal(row.get(3)));
            bar.setClose(decimal(row.get(4)));
            bar.setVolume(decimal(row.get(5)));
            bar.setAmount(decimal(row.get(7)));
            if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0 && bar.getClose() != null) {
                bar.setChangePct(bar.getClose().subtract(prevClose)
                        .divide(prevClose, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP));
            }
            prevClose = bar.getClose();
            out.add(bar);
        }
        return out;
    }

    private JsonNode getJson(URI uri, String tag) {
        try {
            return objectMapper.readTree(getText(uri, tag));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析币安响应失败(" + tag + "): " + e.getMessage(), e);
        }
    }

    private String getText(URI uri, String tag) {
        try {
            String body = restTemplate.getForObject(uri, String.class);
            if (body == null || body.trim().isEmpty()) {
                throw new IllegalStateException("币安返回空响应(" + tag + ")");
            }
            return body;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new IllegalArgumentException("币安拒绝请求(" + tag + ")");
            }
            throw new IllegalStateException("币安请求失败(" + tag + "): HTTP " + e.getRawStatusCode(), e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("币安请求失败(" + tag + "): " + e.getMessage(), e);
        }
    }

    private String baseUrl() {
        String url = props.getBinance().getBaseUrl();
        if (url == null || url.trim().isEmpty()) {
            return "https://api.binance.com";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url.trim();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }

    private static BigDecimal decimal(JsonNode n, String field) {
        return decimal(n.path(field));
    }

    private static BigDecimal decimal(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        String s = n.asText();
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
