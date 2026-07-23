package com.stock.signal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.domain.SignalRecord;
import com.stock.domain.StockPool;
import com.stock.indicator.IndicatorSeries;
import com.stock.indicator.IndicatorService;
import com.stock.repository.SignalRecordRepository;
import com.stock.repository.StockPoolRepository;
import com.stock.tracking.Low9SignalSavedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 信号评估与扫描服务。
 */
@Slf4j
@Service
public class SignalService {

    private final IndicatorService indicatorService;
    private final SignalEngine signalEngine;
    private final StockPoolRepository poolRepository;
    private final SignalRecordRepository signalRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public SignalService(IndicatorService indicatorService, SignalEngine signalEngine,
                         StockPoolRepository poolRepository, SignalRecordRepository signalRepository,
                         ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.indicatorService = indicatorService;
        this.signalEngine = signalEngine;
        this.poolRepository = poolRepository;
        this.signalRepository = signalRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 评估单只股票最新信号（不落库）。 */
    public Optional<SignalResult> evaluate(String code) {
        return evaluate(code, null);
    }

    /** 评估单只股票信号；asOf 非空时截至该交易日（历史评估/调试，不落库）。 */
    public Optional<SignalResult> evaluate(String code, java.time.LocalDate asOf) {
        IndicatorSeries series = asOf == null
                ? indicatorService.compute(code)
                : indicatorService.computeAsOf(code, asOf);
        Optional<SignalResult> result = signalEngine.evaluate(series);
        result.ifPresent(r -> poolRepository.findByCode(code)
                .ifPresent(p -> r.setName(p.getName())));
        return result;
    }

    /**
     * 扫描全部启用的自选股，命中则落库（同日同向去重）。
     *
     * @return 本轮命中的信号列表
     */
    @Transactional
    public List<SignalResult> scanEnabled() {
        List<StockPool> pool = poolRepository.findByEnabledTrue();
        List<SignalResult> hits = new ArrayList<>();
        for (StockPool stock : pool) {
            try {
                IndicatorSeries series = indicatorService.compute(stock.getCode());
                Optional<SignalResult> opt = signalEngine.evaluate(series);
                if (!opt.isPresent()) {
                    continue;
                }
                SignalResult r = opt.get();
                r.setName(stock.getName());
                hits.add(r);
                saveIfAbsent(r);
            } catch (Exception e) {
                log.error("扫描股票失败 code={} err={}", stock.getCode(), e.getMessage());
            }
        }
        log.info("信号扫描完成: 自选股 {} 只, 命中 {} 条", pool.size(), hits.size());
        return hits;
    }

    /**
     * 落库；若同标的同日同向已存在则不重复创建。
     *
     * @return 新建的记录；若已存在返回空。
     */
    @Transactional
    public Optional<SignalRecord> saveIfAbsent(SignalResult r) {
        if (signalRepository.existsByCodeAndTradeDateAndSignalType(
                r.getCode(), r.getTradeDate(), r.getType())) {
            return Optional.empty();
        }
        SignalRecord rec = new SignalRecord();
        rec.setCode(r.getCode());
        rec.setName(r.getName());
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
        SignalRecord saved = signalRepository.save(rec);
        if (saved.getSignalType() == SignalType.BUY_LOW9) {
            eventPublisher.publishEvent(new Low9SignalSavedEvent(saved));
        }
        return Optional.of(saved);
    }

    /** 信号查询（可按代码/日期/类型过滤）。 */
    public List<SignalRecord> query(String code, LocalDate date, SignalType type) {
        List<SignalRecord> base;
        if (code != null && !code.isEmpty()) {
            base = signalRepository.findByCodeOrderByTradeDateDesc(code);
        } else if (date != null) {
            base = signalRepository.findByTradeDateOrderByStrongDescScoreDesc(date);
        } else {
            base = signalRepository.findTop200ByOrderByTradeDateDescIdDesc();
        }
        List<SignalRecord> result = new ArrayList<>();
        for (SignalRecord r : base) {
            if (date != null && !date.equals(r.getTradeDate())) {
                continue;
            }
            if (type != null && type != r.getSignalType()) {
                continue;
            }
            result.add(r);
        }
        return result;
    }

    /** 标记已通知。 */
    @Transactional
    public void markNotified(SignalRecord rec, String channel) {
        rec.setNotified(true);
        rec.setNotifyChannel(channel);
        signalRepository.save(rec);
    }

    /** 冷却判定：cooldownDays 天内（不含当日）同标的同向已有"已通知"记录则处于冷却中。 */
    public boolean inCooldown(String code, com.stock.signal.SignalType type, LocalDate date, int cooldownDays) {
        if (cooldownDays <= 0) {
            return false;
        }
        LocalDate start = date.minusDays(cooldownDays);
        LocalDate end = date.minusDays(1);
        return signalRepository.findByCodeAndSignalTypeAndTradeDateBetween(code, type, start, end)
                .stream().anyMatch(SignalRecord::isNotified);
    }
}
