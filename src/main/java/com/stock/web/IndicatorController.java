package com.stock.web;

import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import com.stock.indicator.IndicatorService;
import com.stock.scan.RealtimeScanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 指标查询接口（供绘图/调试）。
 */
@RestController
public class IndicatorController {

    private final IndicatorService indicatorService;
    private final RealtimeScanService realtimeScanService;

    public IndicatorController(IndicatorService indicatorService, RealtimeScanService realtimeScanService) {
        this.indicatorService = indicatorService;
        this.realtimeScanService = realtimeScanService;
    }

    /**
     * 返回已落库日K的指标序列。limit>0 时只返回最近 limit 根。
     */
    @GetMapping("/api/indicator")
    public List<BarIndicator> indicator(@RequestParam String code,
                                        @RequestParam(required = false, defaultValue = "0") int limit) {
        return trim(indicatorService.compute(code), limit);
    }

    /**
     * 返回含当日实时未收盘K线的指标序列（盘中刷新用）。
     */
    @GetMapping("/api/indicator/realtime")
    public List<BarIndicator> realtime(@RequestParam String code,
                                       @RequestParam(required = false, defaultValue = "0") int limit) {
        return trim(realtimeScanService.computeRealtimeSeries(code), limit);
    }

    private List<BarIndicator> trim(IndicatorSeries series, int limit) {
        List<BarIndicator> bars = series.getBars();
        if (limit > 0 && bars.size() > limit) {
            return bars.subList(bars.size() - limit, bars.size());
        }
        return bars;
    }
}
