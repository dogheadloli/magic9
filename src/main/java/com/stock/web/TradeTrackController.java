package com.stock.web;

import com.stock.tracking.TradeTrackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 低9交易跟踪维护接口。
 */
@RestController
@RequestMapping("/api/trade-tracks")
public class TradeTrackController {

    private final TradeTrackService tradeTrackService;

    public TradeTrackController(TradeTrackService tradeTrackService) {
        this.tradeTrackService = tradeTrackService;
    }

    /** 手动补同步尚未进入跟踪表的历史低9信号。 */
    @PostMapping("/sync")
    public TradeTrackService.SyncResult sync() {
        return tradeTrackService.syncExistingSignals();
    }

    /** 用已落库日K重建全部状态，修复无效实时行情造成的误判。 */
    @PostMapping("/rebuild")
    public TradeTrackService.RebuildResult rebuild() {
        return tradeTrackService.rebuildAllFromFinalizedKlines();
    }

    /** 手动触发状态检查；realtime=true 时拼接当日实时K线。 */
    @PostMapping("/check")
    public Map<String, Object> check(@RequestParam(defaultValue = "true") boolean realtime) {
        int changed = tradeTrackService.checkOpenTrades(realtime);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changed", changed);
        result.put("realtime", realtime);
        return result;
    }
}
