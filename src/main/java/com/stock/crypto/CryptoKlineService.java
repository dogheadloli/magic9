package com.stock.crypto;

import com.stock.indicator.PriceBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 币安 K 线回补与落库。
 */
@Slf4j
@Service
public class CryptoKlineService {

    private final BinanceSpotClient binance;
    private final CryptoKlineRepository repository;
    private final CryptoProperties props;

    public CryptoKlineService(BinanceSpotClient binance, CryptoKlineRepository repository,
                              CryptoProperties props) {
        this.binance = binance;
        this.repository = repository;
        this.props = props;
    }

    @Transactional
    public int backfill(String symbol, CryptoInterval interval) {
        return upsert(symbol, interval, binance.fetchKlines(symbol, interval, props.historyBars(interval)));
    }

    @Transactional
    public Map<CryptoInterval, Integer> backfillAllIntervals(String symbol) {
        Map<CryptoInterval, Integer> result = new LinkedHashMap<CryptoInterval, Integer>();
        result.put(CryptoInterval.D1, backfill(symbol, CryptoInterval.D1));
        result.put(CryptoInterval.H4, backfill(symbol, CryptoInterval.H4));
        return result;
    }

    /**
     * 拉取最近若干根（含未收盘）并 upsert；库内过少时自动全量回补。
     */
    @Transactional
    public int refreshLatest(String symbol, CryptoInterval interval) {
        long count = repository.countBySymbolAndInterval(symbol, interval);
        if (count < 80) {
            return backfill(symbol, interval);
        }
        return upsert(symbol, interval,
                binance.fetchKlines(symbol, interval, props.getFetch().getLiveBars()));
    }

    public List<CryptoKline> getKline(String symbol, CryptoInterval interval) {
        return repository.findBySymbolAndIntervalOrderByOpenTimeAsc(symbol, interval);
    }

    public List<PriceBar> toPriceBars(List<CryptoKline> klines) {
        List<PriceBar> bars = new ArrayList<PriceBar>(klines.size());
        for (CryptoKline k : klines) {
            PriceBar p = new PriceBar();
            p.setTradeDate(k.getTradeDate());
            p.setOpenTime(k.getOpenTime());
            p.setOpen(toD(k.getOpen()));
            p.setHigh(toD(k.getHigh()));
            p.setLow(toD(k.getLow()));
            p.setClose(toD(k.getClose()));
            p.setVolume(k.getVolume() == null ? 0d : k.getVolume().doubleValue());
            p.setChangePct(k.getChangePct() == null ? null : k.getChangePct().doubleValue());
            bars.add(p);
        }
        return bars;
    }

    private int upsert(String symbol, CryptoInterval interval, List<CryptoKlineBar> bars) {
        int saved = 0;
        for (CryptoKlineBar bar : bars) {
            if (bar.getOpenTime() == null) {
                continue;
            }
            CryptoKline entity = repository
                    .findBySymbolAndIntervalAndOpenTime(symbol, interval, bar.getOpenTime())
                    .orElseGet(CryptoKline::new);
            entity.setSymbol(symbol);
            entity.setInterval(interval);
            entity.setOpenTime(bar.getOpenTime());
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
        log.info("币安K线落库 symbol={} interval={} bars={}", symbol, interval, saved);
        return saved;
    }

    private static double toD(java.math.BigDecimal v) {
        return v == null ? Double.NaN : v.doubleValue();
    }
}
