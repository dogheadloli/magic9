package com.stock.scan;

import com.google.common.util.concurrent.RateLimiter;
import com.stock.alert.AlertService;
import com.stock.config.AlertProperties;
import com.stock.datafetch.MarketDataProvider;
import com.stock.datafetch.StockQuote;
import com.stock.domain.KlineDaily;
import com.stock.domain.Market;
import com.stock.domain.SignalRecord;
import com.stock.domain.StockPool;
import com.stock.indicator.IndicatorSeries;
import com.stock.indicator.IndicatorService;
import com.stock.repository.KlineDailyRepository;
import com.stock.repository.StockPoolRepository;
import com.stock.signal.SignalEngine;
import com.stock.signal.SignalResult;
import com.stock.signal.SignalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 盘中实时扫描：拉取实时快照拼接"当日未收盘K线"→ 计算指标 → 评估信号 → 去重冷却 → 推送。
 */
@Slf4j
@Service
public class RealtimeScanService {

    private final StockPoolRepository poolRepository;
    private final KlineDailyRepository klineRepository;
    private final MarketDataProvider provider;
    private final IndicatorService indicatorService;
    private final SignalEngine signalEngine;
    private final SignalService signalService;
    private final AlertService alertService;
    private final AlertProperties alertProps;
    private final ExecutorService scanExecutor;
    private final RateLimiter scanRateLimiter;
    private final com.stock.config.ScheduleProperties scheduleProps;
    private final TradeCalendar tradeCalendar;

    public RealtimeScanService(StockPoolRepository poolRepository, KlineDailyRepository klineRepository,
                               MarketDataProvider provider, IndicatorService indicatorService,
                               SignalEngine signalEngine, SignalService signalService,
                               AlertService alertService, AlertProperties alertProps,
                               ExecutorService scanExecutor, RateLimiter scanRateLimiter,
                               com.stock.config.ScheduleProperties scheduleProps,
                               TradeCalendar tradeCalendar) {
        this.poolRepository = poolRepository;
        this.klineRepository = klineRepository;
        this.provider = provider;
        this.indicatorService = indicatorService;
        this.signalEngine = signalEngine;
        this.signalService = signalService;
        this.alertService = alertService;
        this.alertProps = alertProps;
        this.scanExecutor = scanExecutor;
        this.scanRateLimiter = scanRateLimiter;
        this.scheduleProps = scheduleProps;
        this.tradeCalendar = tradeCalendar;
    }

    /**
     * 扫描全部启用自选股（并发 + 限流），命中即落库去重并推送。
     *
     * @return 本轮命中的信号列表
     */
    public List<SignalResult> scan() {
        List<StockPool> pool = poolRepository.findByEnabledTrue();
        List<Future<Optional<SignalResult>>> futures = new ArrayList<>();
        for (StockPool stock : pool) {
            Callable<Optional<SignalResult>> task = () -> scanOne(stock);
            futures.add(scanExecutor.submit(task));
        }
        List<SignalResult> hits = new ArrayList<>();
        for (Future<Optional<SignalResult>> f : futures) {
            try {
                f.get().ifPresent(hits::add);
            } catch (Exception e) {
                log.error("实时扫描任务异常 err={}", e.getMessage());
            }
        }
        log.info("实时扫描完成: 自选股 {} 只, 命中 {} 条", pool.size(), hits.size());
        return hits;
    }

    private Optional<SignalResult> scanOne(StockPool stock) {
        String code = stock.getCode();
        Market market = stock.getMarket() != null ? stock.getMarket() : Market.inferByCode(code);
        try {
            List<KlineDaily> assembled = assembleRealtime(code, market);
            IndicatorSeries series = indicatorService.compute(code, assembled);
            Optional<SignalResult> opt = signalEngine.evaluate(series);
            if (!opt.isPresent()) {
                return Optional.empty();
            }
            SignalResult r = opt.get();
            r.setName(stock.getName());
            notifyIfNeeded(r);
            return Optional.of(r);
        } catch (Exception e) {
            log.error("实时扫描股票失败 code={} err={}", code, e.getMessage());
            return Optional.empty();
        }
    }

    /** 计算含当日实时未收盘K线的指标序列（供前端盘中刷新）。 */
    public IndicatorSeries computeRealtimeSeries(String code) {
        Market market = poolRepository.findByCode(code)
                .map(StockPool::getMarket)
                .orElseGet(() -> Market.inferByCode(code));
        List<KlineDaily> assembled = assembleRealtime(code, market);
        return indicatorService.compute(code, assembled);
    }

    /** 历史日K + 实时快照拼接为当日未收盘K线。 */
    private List<KlineDaily> assembleRealtime(String code, Market market) {
        List<KlineDaily> history = klineRepository.findByCodeOrderByTradeDateAsc(code);
        List<KlineDaily> assembled = new ArrayList<>(history);
        if (!tradeCalendar.hasTradingStartedToday()) {
            return assembled;
        }
        StockQuote q = fetchQuoteWithRetry(code, market);
        if (!isUsableQuote(q)) {
            return assembled;
        }
        LocalDate today = LocalDate.now();
        KlineDaily bar = new KlineDaily();
        bar.setCode(code);
        bar.setTradeDate(today);
        bar.setOpen(q.getOpen());
        bar.setHigh(q.getHigh());
        bar.setLow(q.getLow());
        bar.setClose(q.getPrice());
        bar.setVolume(q.getVolume() == null ? 0L : q.getVolume());
        bar.setChangePct(q.getChangePct());
        if (!assembled.isEmpty() && today.equals(assembled.get(assembled.size() - 1).getTradeDate())) {
            assembled.set(assembled.size() - 1, bar);   // 覆盖当日临时K线
        } else {
            assembled.add(bar);
        }
        return assembled;
    }

    private StockQuote fetchQuoteWithRetry(String code, Market market) {
        int retry = Math.max(1, scheduleProps.getRetry());
        for (int i = 0; i < retry; i++) {
            scanRateLimiter.acquire();
            StockQuote q = provider.fetchQuote(code, market);
            if (isUsableQuote(q)) {
                return q;
            }
        }
        log.warn("实时行情无效，忽略当日临时K线 code={}", code);
        return null;
    }

    /**
     * 开盘前部分行情源会返回 price/open/high/low=0。此类快照绝不能拼入K线，
     * 否则 low=0 会误触发止损，close=0 也会污染图表与指标。
     */
    static boolean isUsableQuote(StockQuote q) {
        if (q == null || !positive(q.getPrice()) || !positive(q.getOpen())
                || !positive(q.getHigh()) || !positive(q.getLow())) {
            return false;
        }
        BigDecimal max = q.getHigh();
        BigDecimal min = q.getLow();
        return max.compareTo(min) >= 0
                && max.compareTo(q.getPrice()) >= 0
                && min.compareTo(q.getPrice()) <= 0
                && max.compareTo(q.getOpen()) >= 0
                && min.compareTo(q.getOpen()) <= 0;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private void notifyIfNeeded(SignalResult r) {
        Optional<SignalRecord> saved = signalService.saveIfAbsent(r);
        if (!saved.isPresent()) {
            return;   // 同日同向已存在，去重
        }
        if (signalService.inCooldown(r.getCode(), r.getType(), r.getTradeDate(), alertProps.getCooldownDays())) {
            log.info("冷却中跳过推送 code={} type={}", r.getCode(), r.getType());
            return;
        }
        String channels = alertService.dispatch(r);
        if (!channels.isEmpty()) {
            signalService.markNotified(saved.get(), channels);
        }
    }
}
