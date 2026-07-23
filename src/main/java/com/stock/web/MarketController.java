package com.stock.web;

import com.stock.scan.TradeCalendar;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 市场/交易状态接口（供前端判断是否盘中、是否自动刷新）。
 */
@RestController
public class MarketController {

    private final TradeCalendar tradeCalendar;

    public MarketController(TradeCalendar tradeCalendar) {
        this.tradeCalendar = tradeCalendar;
    }

    @GetMapping("/api/market/status")
    public Map<String, Object> status() {
        boolean tradingDay = tradeCalendar.isTradingDay(LocalDate.now());
        boolean inSession = tradeCalendar.isInTradingSession(LocalTime.now());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tradingDay", tradingDay);
        m.put("inSession", inSession);
        m.put("active", tradingDay && inSession);
        m.put("refreshMs", 60000);
        m.put("serverTime", LocalDateTime.now().toString());
        return m;
    }
}
