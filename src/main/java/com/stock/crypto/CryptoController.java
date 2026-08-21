package com.stock.crypto;

import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import com.stock.signal.SignalResult;
import com.stock.signal.SignalType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 币安现货监控接口（与 /api/pool、/api/kline 等股票接口隔离）。
 */
@RestController
@RequestMapping("/api/crypto")
public class CryptoController {

    private final CryptoPoolService poolService;
    private final CryptoKlineService klineService;
    private final CryptoSignalService signalService;
    private final CryptoScanService scanService;
    private final BinanceSpotClient binance;
    private final CryptoProperties props;

    public CryptoController(CryptoPoolService poolService, CryptoKlineService klineService,
                            CryptoSignalService signalService, CryptoScanService scanService,
                            BinanceSpotClient binance, CryptoProperties props) {
        this.poolService = poolService;
        this.klineService = klineService;
        this.signalService = signalService;
        this.scanService = scanService;
        this.binance = binance;
        this.props = props;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("enabled", props.isEnabled());
        m.put("scanEnabled", props.getScan().isEnabled());
        m.put("active", props.isEnabled() && props.getScan().isEnabled());
        m.put("exchange", "BINANCE_SPOT");
        m.put("intervals", new String[]{"D1", "H4"});
        return m;
    }

    @GetMapping("/pool")
    public List<CryptoPool> pool() {
        return poolService.list();
    }

    @PostMapping("/pool")
    public CryptoPool add(@Valid @RequestBody AddCryptoRequest req) {
        CryptoPool pool = poolService.add(req.getSymbol(), req.getName(), req.getGroup());
        try {
            klineService.backfillAllIntervals(pool.getSymbol());
        } catch (Exception ignored) {
            // 添加成功即可，K线可稍后手动回补
        }
        return pool;
    }

    @DeleteMapping("/pool/{id}")
    public void delete(@PathVariable Long id) {
        poolService.delete(id);
    }

    @PutMapping("/pool/{id}/enabled")
    public CryptoPool setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return poolService.setEnabled(id, enabled);
    }

    @PostMapping("/kline/backfill")
    public Map<String, Object> backfill(@RequestParam String symbol,
                                        @RequestParam(required = false) String interval) {
        String sym = CryptoPoolService.normalizeSymbol(symbol);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("symbol", sym);
        if (interval == null || interval.trim().isEmpty()) {
            result.put("saved", klineService.backfillAllIntervals(sym));
        } else {
            CryptoInterval it = CryptoInterval.fromParam(interval);
            result.put("interval", it.name());
            result.put("saved", klineService.backfill(sym, it));
        }
        return result;
    }

    @GetMapping("/kline")
    public List<CryptoKline> kline(@RequestParam String symbol,
                                   @RequestParam(defaultValue = "D1") String interval) {
        return klineService.getKline(CryptoPoolService.normalizeSymbol(symbol),
                CryptoInterval.fromParam(interval));
    }

    @GetMapping("/quote")
    public CryptoQuote quote(@RequestParam String symbol) {
        String sym = CryptoPoolService.normalizeSymbol(symbol);
        CryptoQuote q = binance.fetchQuote(sym);
        Optional<CryptoPool> pool = poolService.findBySymbol(sym);
        q.setName(pool.isPresent() && pool.get().getName() != null ? pool.get().getName() : sym);
        return q;
    }

    @GetMapping("/indicator")
    public List<BarIndicator> indicator(@RequestParam String symbol,
                                        @RequestParam(defaultValue = "D1") String interval,
                                        @RequestParam(defaultValue = "false") boolean live) {
        String sym = CryptoPoolService.normalizeSymbol(symbol);
        CryptoInterval it = CryptoInterval.fromParam(interval);
        if (live) {
            klineService.refreshLatest(sym, it);
        }
        return trim(signalService.compute(sym, it), 0);
    }

    @GetMapping("/signal/evaluate")
    public List<SignalResult> evaluate(@RequestParam String symbol,
                                       @RequestParam(defaultValue = "D1") String interval) {
        Optional<SignalResult> r = signalService.evaluate(
                CryptoPoolService.normalizeSymbol(symbol), CryptoInterval.fromParam(interval));
        return r.isPresent() ? Collections.singletonList(r.get()) : Collections.<SignalResult>emptyList();
    }

    @PostMapping("/scan")
    public List<SignalResult> scan() {
        return scanService.scanAll();
    }

    @GetMapping("/signals")
    public List<CryptoSignal> signals(@RequestParam(required = false) String symbol,
                                      @RequestParam(required = false) String interval,
                                      @RequestParam(required = false) SignalType type) {
        CryptoInterval it = (interval == null || interval.trim().isEmpty())
                ? null : CryptoInterval.fromParam(interval);
        return signalService.query(symbol, it, type);
    }

    private List<BarIndicator> trim(IndicatorSeries series, int limit) {
        List<BarIndicator> bars = series.getBars();
        if (limit > 0 && bars.size() > limit) {
            return bars.subList(bars.size() - limit, bars.size());
        }
        return bars;
    }
}
