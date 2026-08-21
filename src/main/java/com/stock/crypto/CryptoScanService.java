package com.stock.crypto;

import com.google.common.util.concurrent.RateLimiter;
import com.stock.alert.AlertService;
import com.stock.config.AlertProperties;
import com.stock.indicator.IndicatorSeries;
import com.stock.signal.SignalEngine;
import com.stock.signal.SignalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 24/7 扫描：刷新最近 K 线 → 评估日K/4H 信号 → 去重冷却 → 推送。
 */
@Slf4j
@Service
public class CryptoScanService {

    private final CryptoPoolRepository poolRepository;
    private final CryptoKlineService klineService;
    private final CryptoSignalService signalService;
    private final SignalEngine signalEngine;
    private final AlertService alertService;
    private final AlertProperties alertProps;
    private final CryptoProperties props;
    private final ExecutorService executor;
    private final RateLimiter rateLimiter;

    public CryptoScanService(CryptoPoolRepository poolRepository, CryptoKlineService klineService,
                             CryptoSignalService signalService, SignalEngine signalEngine,
                             AlertService alertService, AlertProperties alertProps,
                             CryptoProperties props,
                             @Qualifier("cryptoScanExecutor") ExecutorService executor,
                             @Qualifier("cryptoRateLimiter") RateLimiter rateLimiter) {
        this.poolRepository = poolRepository;
        this.klineService = klineService;
        this.signalService = signalService;
        this.signalEngine = signalEngine;
        this.alertService = alertService;
        this.alertProps = alertProps;
        this.props = props;
        this.executor = executor;
        this.rateLimiter = rateLimiter;
    }

    public List<SignalResult> scanAll() {
        List<CryptoPool> pool = poolRepository.findByEnabledTrue();
        List<Future<List<SignalResult>>> futures = new ArrayList<Future<List<SignalResult>>>();
        for (final CryptoPool item : pool) {
            Callable<List<SignalResult>> task = new Callable<List<SignalResult>>() {
                @Override
                public List<SignalResult> call() {
                    return scanOne(item);
                }
            };
            futures.add(executor.submit(task));
        }
        List<SignalResult> hits = new ArrayList<SignalResult>();
        for (Future<List<SignalResult>> f : futures) {
            try {
                hits.addAll(f.get());
            } catch (Exception e) {
                log.error("加密扫描任务异常 err={}", e.getMessage());
            }
        }
        log.info("加密扫描完成: 交易对 {} 个, 命中 {} 条", pool.size(), hits.size());
        return hits;
    }

    public List<SignalResult> scanOne(CryptoPool item) {
        List<SignalResult> hits = new ArrayList<SignalResult>();
        for (CryptoInterval interval : Arrays.asList(CryptoInterval.D1, CryptoInterval.H4)) {
            try {
                Optional<SignalResult> hit = scanInterval(item, interval);
                if (hit.isPresent()) {
                    hits.add(hit.get());
                }
            } catch (Exception e) {
                log.error("加密扫描失败 symbol={} interval={} err={}",
                        item.getSymbol(), interval, e.getMessage());
            }
        }
        return hits;
    }

    private Optional<SignalResult> scanInterval(CryptoPool item, CryptoInterval interval) {
        refreshWithRetry(item.getSymbol(), interval);
        IndicatorSeries series = signalService.compute(item.getSymbol(), interval);
        Optional<SignalResult> opt = signalEngine.evaluate(series);
        if (!opt.isPresent()) {
            return Optional.empty();
        }
        SignalResult r = opt.get();
        signalService.decorate(r, item.getSymbol(), interval, series);
        notifyIfNeeded(r, interval);
        return Optional.of(r);
    }

    private void refreshWithRetry(String symbol, CryptoInterval interval) {
        int retry = Math.max(1, props.getScan().getRetry());
        Exception last = null;
        for (int i = 0; i < retry; i++) {
            rateLimiter.acquire();
            try {
                klineService.refreshLatest(symbol, interval);
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalStateException("刷新K线失败 " + symbol + " " + interval
                + (last == null ? "" : ": " + last.getMessage()), last);
    }

    private void notifyIfNeeded(SignalResult r, CryptoInterval interval) {
        Optional<CryptoSignal> saved = signalService.saveIfAbsent(r, interval);
        if (!saved.isPresent()) {
            return;
        }
        if (signalService.inCooldown(r.getCode(), interval, r.getType(), r.getBarTime(),
                alertProps.getCooldownDays())) {
            log.info("加密信号冷却中 symbol={} interval={} type={}", r.getCode(), interval, r.getType());
            return;
        }
        String channels = alertService.dispatch(r);
        if (channels != null && !channels.isEmpty()) {
            signalService.markNotified(saved.get(), channels);
        }
    }
}
