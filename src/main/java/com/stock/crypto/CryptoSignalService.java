package com.stock.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import com.stock.indicator.IndicatorService;
import com.stock.signal.SignalEngine;
import com.stock.signal.SignalResult;
import com.stock.signal.SignalType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 加密货币信号评估与落库（与股票 signal_record 隔离）。
 */
@Slf4j
@Service
public class CryptoSignalService {

    private final CryptoKlineService klineService;
    private final IndicatorService indicatorService;
    private final SignalEngine signalEngine;
    private final CryptoSignalRepository signalRepository;
    private final CryptoPoolRepository poolRepository;
    private final CryptoProperties props;
    private final ObjectMapper objectMapper;

    public CryptoSignalService(CryptoKlineService klineService, IndicatorService indicatorService,
                               SignalEngine signalEngine, CryptoSignalRepository signalRepository,
                               CryptoPoolRepository poolRepository, CryptoProperties props,
                               ObjectMapper objectMapper) {
        this.klineService = klineService;
        this.indicatorService = indicatorService;
        this.signalEngine = signalEngine;
        this.signalRepository = signalRepository;
        this.poolRepository = poolRepository;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public IndicatorSeries compute(String symbol, CryptoInterval interval) {
        return indicatorService.computeFrom(symbol, klineService.toPriceBars(
                klineService.getKline(symbol, interval)));
    }

    public Optional<SignalResult> evaluate(String symbol, CryptoInterval interval) {
        IndicatorSeries series = compute(symbol, interval);
        Optional<SignalResult> result = signalEngine.evaluate(series);
        if (result.isPresent()) {
            decorate(result.get(), symbol, interval, series);
        }
        return result;
    }

    public void decorate(SignalResult r, String symbol, CryptoInterval interval, IndicatorSeries series) {
        r.setCode(symbol);
        r.setAssetKind("币安现货");
        r.setInterval(interval.name());
        poolRepository.findBySymbol(symbol).ifPresent(p -> r.setName(displayName(p, interval)));
        if (r.getName() == null || r.getName().isEmpty()) {
            r.setName(symbol + " · " + interval.getLabel());
        }
        BarIndicator last = series.isEmpty() ? null : series.last();
        if (last != null) {
            r.setBarTime(last.getOpenTime());
            r.setTradeDate(last.getTradeDate());
            LocalDate exit = last.getTradeDate().plusDays(props.getHoldCalendarDays());
            r.setLatestExitDate(exit);
            r.getDetail().put("latestExitDate", String.valueOf(exit));
            r.getDetail().put("interval", interval.getBinance());
            if (last.getOpenTime() != null) {
                r.getDetail().put("openTime", last.getOpenTime().toString());
            }
        }
    }

    @Transactional
    public Optional<CryptoSignal> saveIfAbsent(SignalResult r, CryptoInterval interval) {
        LocalDateTime openTime = r.getBarTime();
        if (openTime == null) {
            return Optional.empty();
        }
        if (signalRepository.existsBySymbolAndIntervalAndOpenTimeAndSignalType(
                r.getCode(), interval, openTime, r.getType())) {
            return Optional.empty();
        }
        CryptoSignal rec = new CryptoSignal();
        rec.setSymbol(r.getCode());
        rec.setName(r.getName());
        rec.setInterval(interval);
        rec.setOpenTime(openTime);
        rec.setTradeDate(r.getTradeDate());
        rec.setSignalType(r.getType());
        rec.setScore(r.getScore());
        rec.setMaxScore(r.getMaxScore());
        rec.setStrong(r.isStrong());
        rec.setHitFactors(r.getHitFactorsCsv());
        rec.setEntryPrice(r.getEntryPrice());
        rec.setStopPrice(r.getStopPrice());
        rec.setTargetPrice(r.getTargetPrice());
        rec.setLatestExitDate(r.getLatestExitDate());
        try {
            rec.setDetailJson(objectMapper.writeValueAsString(r.getDetail()));
        } catch (Exception e) {
            rec.setDetailJson(null);
        }
        return Optional.of(signalRepository.save(rec));
    }

    @Transactional
    public void markNotified(CryptoSignal rec, String channel) {
        rec.setNotified(true);
        rec.setNotifyChannel(channel);
        signalRepository.save(rec);
    }

    public boolean inCooldown(String symbol, CryptoInterval interval, SignalType type,
                              LocalDateTime openTime, int cooldownDays) {
        if (cooldownDays <= 0 || openTime == null) {
            return false;
        }
        LocalDateTime start = openTime.minusDays(cooldownDays);
        LocalDateTime end = openTime.minusSeconds(1);
        List<CryptoSignal> rows = signalRepository
                .findBySymbolAndIntervalAndSignalTypeAndOpenTimeBetween(symbol, interval, type, start, end);
        for (CryptoSignal row : rows) {
            if (row.isNotified()) {
                return true;
            }
        }
        return false;
    }

    public List<CryptoSignal> query(String symbol, CryptoInterval interval, SignalType type) {
        List<CryptoSignal> base = (symbol != null && !symbol.trim().isEmpty())
                ? signalRepository.findBySymbolOrderByOpenTimeDesc(CryptoPoolService.normalizeSymbol(symbol))
                : signalRepository.findTop200ByOrderByOpenTimeDescIdDesc();
        List<CryptoSignal> result = new ArrayList<CryptoSignal>();
        for (CryptoSignal r : base) {
            if (interval != null && r.getInterval() != interval) {
                continue;
            }
            if (type != null && type != r.getSignalType()) {
                continue;
            }
            result.add(r);
        }
        return result;
    }

    private static String displayName(CryptoPool pool, CryptoInterval interval) {
        String base = pool.getName() != null && !pool.getName().isEmpty() ? pool.getName() : pool.getSymbol();
        return base + " · " + interval.getLabel();
    }
}
