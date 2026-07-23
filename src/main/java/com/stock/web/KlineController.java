package com.stock.web;

import com.stock.datafetch.MarketDataProvider;
import com.stock.datafetch.StockQuote;
import com.stock.domain.KlineDaily;
import com.stock.domain.Market;
import com.stock.service.KlineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日K查询与回补接口。
 */
@RestController
public class KlineController {

    private final KlineService klineService;
    private final MarketDataProvider provider;

    public KlineController(KlineService klineService, MarketDataProvider provider) {
        this.klineService = klineService;
        this.provider = provider;
    }

    /** 回补指定股票日K（前复权）。 */
    @PostMapping("/api/kline/backfill")
    public Map<String, Object> backfill(@RequestParam String code) {
        int count = klineService.backfill(code, Market.inferByCode(code));
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("saved", count);
        return result;
    }

    /** 查询已落库的日K线。 */
    @GetMapping("/api/kline")
    public List<KlineDaily> kline(@RequestParam String code) {
        return klineService.getKline(code);
    }

    /** 拉取实时行情快照。 */
    @GetMapping("/api/quote")
    public StockQuote quote(@RequestParam String code) {
        return provider.fetchQuote(code, Market.inferByCode(code));
    }
}
