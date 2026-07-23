package com.stock.web;

import com.stock.datafetch.MarketDataProvider;
import com.stock.datafetch.StockQuote;
import com.stock.domain.StockPool;
import com.stock.repository.StockPoolRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量行情接口（供行情看板/隐蔽页使用）。
 * <p>统一走腾讯批量查询接口，每 {@link #BATCH_SIZE} 只一批，减少请求数。
 */
@RestController
public class QuoteController {

    private static final int BATCH_SIZE = 10;

    private final StockPoolRepository poolRepository;
    private final MarketDataProvider provider;

    public QuoteController(StockPoolRepository poolRepository, MarketDataProvider provider) {
        this.poolRepository = poolRepository;
        this.provider = provider;
    }

    /**
     * 返回实时行情（名称/代码/现价/涨跌/涨跌幅）。
     *
     * @param codes 逗号分隔的代码；为空则取全部启用自选股。
     */
    @GetMapping("/api/quotes")
    public List<Map<String, Object>> quotes(@RequestParam(required = false) String codes) {
        Map<String, StockPool> poolByCode = new LinkedHashMap<>();
        for (StockPool s : poolRepository.findByEnabledTrue()) {
            poolByCode.put(s.getCode(), s);
        }

        List<String> codeList = new ArrayList<>();
        if (codes != null && !codes.trim().isEmpty()) {
            for (String c : codes.split(",")) {
                String t = c.trim();
                if (!t.isEmpty()) {
                    codeList.add(t);
                }
            }
        } else {
            codeList.addAll(poolByCode.keySet());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < codeList.size(); i += BATCH_SIZE) {
            List<String> chunk = codeList.subList(i, Math.min(i + BATCH_SIZE, codeList.size()));
            Map<String, StockQuote> byCode = new HashMap<>();
            for (StockQuote q : provider.fetchQuotes(chunk)) {
                if (q != null && q.getCode() != null) {
                    byCode.put(q.getCode(), q);
                }
            }
            for (String code : chunk) {
                result.add(row(code, poolByCode.get(code), byCode.get(code)));
            }
        }
        return result;
    }

    private Map<String, Object> row(String code, StockPool sp, StockQuote q) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        String name = sp != null && sp.getName() != null
                ? sp.getName()
                : (q != null && q.getName() != null ? q.getName() : code);
        row.put("name", name);
        if (q != null) {
            row.put("price", q.getPrice());
            row.put("changePct", q.getChangePct());
            if (q.getPrice() != null && q.getPreClose() != null) {
                row.put("change", q.getPrice().subtract(q.getPreClose()).setScale(2, RoundingMode.HALF_UP));
            } else {
                row.put("change", null);
            }
        } else {
            row.put("price", null);
            row.put("changePct", null);
            row.put("change", null);
        }
        return row;
    }
}
