package com.stock.service;

import com.stock.datafetch.KlineBar;
import com.stock.datafetch.MarketDataProvider;
import com.stock.domain.KlineDaily;
import com.stock.domain.Market;
import com.stock.repository.KlineDailyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 日K回补与落库服务。
 */
@Slf4j
@Service
public class KlineService {

    private final MarketDataProvider provider;
    private final KlineDailyRepository repository;

    @Value("${market.fetch.history-bars:250}")
    private int historyBars;

    public KlineService(MarketDataProvider provider, KlineDailyRepository repository) {
        this.provider = provider;
        this.repository = repository;
    }

    /**
     * 回补指定股票的日K线（前复权）并落库（按交易日 upsert）。
     *
     * @return 本次写入/更新的根数
     */
    @Transactional
    public int backfill(String code, Market market) {
        Market m = market != null ? market : Market.inferByCode(code);
        List<KlineBar> bars = provider.fetchDailyKline(code, m, historyBars);
        int saved = 0;
        for (KlineBar bar : bars) {
            KlineDaily entity = repository.findByCodeAndTradeDate(code, bar.getTradeDate())
                    .orElseGet(KlineDaily::new);
            entity.setCode(code);
            entity.setTradeDate(bar.getTradeDate());
            entity.setOpen(bar.getOpen());
            entity.setHigh(bar.getHigh());
            entity.setLow(bar.getLow());
            entity.setClose(bar.getClose());
            entity.setVolume(bar.getVolume());
            entity.setAmount(bar.getAmount());
            entity.setChangePct(bar.getChangePct());
            repository.save(entity);
            saved++;
        }
        log.info("回补日K完成 code={} bars={}", code, saved);
        return saved;
    }

    public List<KlineDaily> getKline(String code) {
        return repository.findByCodeOrderByTradeDateAsc(code);
    }

    public Optional<KlineDaily> latest(String code) {
        return repository.findTopByCodeOrderByTradeDateDesc(code);
    }
}
