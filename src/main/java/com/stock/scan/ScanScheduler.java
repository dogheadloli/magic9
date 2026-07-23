package com.stock.scan;

import com.stock.config.ScheduleProperties;
import com.stock.domain.Market;
import com.stock.domain.StockPool;
import com.stock.repository.StockPoolRepository;
import com.stock.service.KlineService;
import com.stock.tracking.TradeTrackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 调度：盘中实时扫描 + 盘前回补 + 收盘定稿。
 */
@Slf4j
@Component
public class ScanScheduler {

    private final ScheduleProperties props;
    private final TradeCalendar tradeCalendar;
    private final RealtimeScanService realtimeScanService;
    private final StockPoolRepository poolRepository;
    private final KlineService klineService;
    private final TradeTrackService tradeTrackService;

    public ScanScheduler(ScheduleProperties props, TradeCalendar tradeCalendar,
                         RealtimeScanService realtimeScanService, StockPoolRepository poolRepository,
                         KlineService klineService, TradeTrackService tradeTrackService) {
        this.props = props;
        this.tradeCalendar = tradeCalendar;
        this.realtimeScanService = realtimeScanService;
        this.poolRepository = poolRepository;
        this.klineService = klineService;
        this.tradeTrackService = tradeTrackService;
    }

    /** 盘中按固定间隔扫描（仅交易日交易时段执行）。 */
    @Scheduled(fixedRateString = "${schedule.intraday-interval-ms:300000}")
    public void intradayScan() {
        if (!props.isIntradayEnabled()) {
            return;
        }
        if (!tradeCalendar.isScanAllowedNow()) {
            return;
        }
        try {
            realtimeScanService.scan();
            tradeTrackService.checkOpenTrades(true);
        } catch (Exception e) {
            log.error("盘中扫描异常 err={}", e.getMessage());
        }
    }

    /** 盘前回补历史日K。 */
    @Scheduled(cron = "${schedule.prefetch-history-cron}")
    public void prefetchHistory() {
        backfillAll("盘前回补");
    }

    /** 收盘后定稿当日K线落库。 */
    @Scheduled(cron = "${schedule.eod-finalize-cron}")
    public void eodFinalize() {
        backfillAll("收盘定稿");
        try {
            tradeTrackService.checkOpenTrades(false);
        } catch (Exception e) {
            log.error("收盘交易计划检查异常 err={}", e.getMessage());
        }
    }

    private void backfillAll(String tag) {
        List<StockPool> pool = poolRepository.findByEnabledTrue();
        int ok = 0;
        for (StockPool stock : pool) {
            try {
                Market m = stock.getMarket() != null ? stock.getMarket() : Market.inferByCode(stock.getCode());
                klineService.backfill(stock.getCode(), m);
                ok++;
            } catch (Exception e) {
                log.error("{}失败 code={} err={}", tag, stock.getCode(), e.getMessage());
            }
        }
        log.info("{}完成: {}/{}", tag, ok, pool.size());
    }
}
