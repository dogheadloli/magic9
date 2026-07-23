package com.stock.backtest;

import com.stock.domain.StockPool;
import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import com.stock.indicator.IndicatorService;
import com.stock.repository.StockPoolRepository;
import com.stock.signal.SignalResult;
import com.stock.signal.SignalEngine;
import com.stock.signal.SignalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 「低9抄底」策略回测：遍历自选股历史，找出所有低9信号，
 * 按不同的【止损 × 止盈 × 持有天数 × 信号强度】组合模拟交易并统计绩效。
 *
 * 交易假设：
 * - 进场：低9完成当根K线收盘价买入（EOD信号系统的常规假设）。
 * - 离场：进场后 1..holdBars 根K线内，盘中触及止损则止损价出，
 *         触及止盈目标则目标价出（同根内先判止损，偏保守）；
 *         到期未触发则持有期最后一根收盘价出。
 */
@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

    private final StockPoolRepository poolRepository;
    private final IndicatorService indicatorService;
    private final SignalEngine signalEngine;

    private static final int[] MIN_SCORES = {1, 2, 3};
    private static final String[] SL_MODES = {"ATR1.5", "ATR2", "PCT5", "PCT8"};
    private static final String[] TP_MODES = {"2R", "3R", "4R", "PCT8", "PCT10", "PCT15", "MA20", "MA60"};
    private static final int[] HOLDS = {3, 5, 8, 10, 15, 20};

    private static final double LOW_BUFFER = 0.005;
    private static final int ATR_PERIOD = 14;

    public BacktestService(StockPoolRepository poolRepository,
                           IndicatorService indicatorService,
                           SignalEngine signalEngine) {
        this.poolRepository = poolRepository;
        this.indicatorService = indicatorService;
        this.signalEngine = signalEngine;
    }

    /** 进场候选信号（低9 + 当时打分）。 */
    private static class Entry {
        String code;
        int idx;
        int score;
        double entry;
        double signalLow;
        double atr;
        List<BarIndicator> bars;
    }

    public BacktestReport run() {
        List<StockPool> pool = poolRepository.findByEnabledTrue();
        List<Entry> entries = new ArrayList<>();
        int stocks = 0;

        for (StockPool sp : pool) {
            IndicatorSeries series;
            try {
                series = indicatorService.compute(sp.getCode());
            } catch (Exception e) {
                log.warn("回测跳过 {}: {}", sp.getCode(), e.getMessage());
                continue;
            }
            if (series == null || series.size() < 30) {
                continue;
            }
            stocks++;
            List<BarIndicator> bars = series.getBars();
            int n = bars.size();
            double[] atr = atr(bars, ATR_PERIOD);
            for (int i = 0; i < n - 1; i++) { // 至少要有一根后续K线
                java.util.Optional<SignalResult> r = signalEngine.composeAt(series, i);
                if (!r.isPresent() || r.get().getType() != SignalType.BUY_LOW9) {
                    continue;
                }
                Entry e = new Entry();
                e.code = sp.getCode();
                e.idx = i;
                e.score = r.get().getScore();
                e.entry = bars.get(i).getClose();
                e.signalLow = bars.get(i).getLow();
                e.atr = atr[i];
                e.bars = bars;
                entries.add(e);
            }
        }

        BacktestReport report = new BacktestReport();
        report.setStocks(stocks);
        report.setTotalLow9(entries.size());
        report.setStopBuffer(LOW_BUFFER);
        report.setAtrPeriod(ATR_PERIOD);
        report.setEntryRule("低9完成当根收盘买入");
        int s1 = 0, s2 = 0, s3 = 0;
        for (Entry e : entries) {
            if (e.score >= 1) s1++;
            if (e.score >= 2) s2++;
            if (e.score >= 3) s3++;
        }
        report.setEntriesScore1(s1);
        report.setEntriesScore2(s2);
        report.setEntriesScore3(s3);

        List<ComboResult> combos = new ArrayList<>();
        for (int minScore : MIN_SCORES) {
            for (String sl : SL_MODES) {
                for (String tp : TP_MODES) {
                    for (int hold : HOLDS) {
                        ComboResult c = simulateCombo(entries, minScore, sl, tp, hold);
                        if (c.getTrades() > 0) {
                            combos.add(c);
                        }
                    }
                }
            }
        }
        // 按期望收益降序；样本太少(<10)的组合排后面
        combos.sort((a, b) -> {
            boolean aw = a.getTrades() >= 10;
            boolean bw = b.getTrades() >= 10;
            if (aw != bw) {
                return aw ? -1 : 1;
            }
            return Double.compare(b.getExpectancyPct(), a.getExpectancyPct());
        });
        report.setCombos(combos);
        if (!combos.isEmpty()) {
            report.setBest(combos.get(0));
        }
        return report;
    }

    private ComboResult simulateCombo(List<Entry> entries, int minScore,
                                      String sl, String tp, int hold) {
        ComboResult c = new ComboResult();
        c.setMinScore(minScore);
        c.setSl(sl);
        c.setTp(tp);
        c.setHoldBars(hold);

        List<Double> rets = new ArrayList<>();
        double sumWin = 0, sumLoss = 0;
        int wins = 0;
        double equity = 1.0, peak = 1.0, maxDd = 0;

        for (Entry e : entries) {
            if (e.score < minScore) {
                continue;
            }
            double stop = stopPrice(sl, e);
            if (Double.isNaN(stop) || stop <= 0 || stop >= e.entry) {
                continue; // 无效止损
            }
            double r = e.entry - stop; // 1R
            Double ret = simulate(e, stop, tp, r, hold);
            if (ret == null) {
                continue;
            }
            rets.add(ret);
            if (ret > 0) {
                wins++;
                sumWin += ret;
            } else {
                sumLoss += ret;
            }
            equity *= (1 + ret / 100.0);
            peak = Math.max(peak, equity);
            double dd = (peak - equity) / peak * 100.0;
            maxDd = Math.max(maxDd, dd);
        }

        int trades = rets.size();
        c.setTrades(trades);
        c.setWins(wins);
        if (trades > 0) {
            double sum = 0;
            for (double v : rets) sum += v;
            c.setWinRatePct(round(wins * 100.0 / trades));
            c.setAvgReturnPct(round(sum / trades));
            c.setExpectancyPct(round(sum / trades));
            c.setAvgWinPct(wins > 0 ? round(sumWin / wins) : 0);
            int losses = trades - wins;
            c.setAvgLossPct(losses > 0 ? round(sumLoss / losses) : 0);
            c.setProfitFactor(sumLoss < 0 ? round(sumWin / -sumLoss) : (sumWin > 0 ? 99.99 : 0));
            c.setMaxDrawdownPct(round(maxDd));
        }
        return c;
    }

    /** 返回该笔交易的收益百分比；若没有后续K线则返回 null。 */
    private Double simulate(Entry e, double stop, String tp, double r, int hold) {
        List<BarIndicator> bars = e.bars;
        int n = bars.size();
        int last = Math.min(e.idx + hold, n - 1);
        if (last <= e.idx) {
            return null;
        }
        boolean maMode = "MA20".equals(tp) || "MA60".equals(tp);
        double target = maMode ? Double.NaN : targetPrice(tp, e.entry, r);
        for (int k = e.idx + 1; k <= last; k++) {
            BarIndicator b = bars.get(k);
            // 同根内先判止损（保守）
            if (b.getLow() <= stop) {
                return pct(stop, e.entry);
            }
            if (maMode) {
                Double ma = "MA20".equals(tp) ? b.getMa20() : b.getMa60();
                if (ma != null && ma > 0 && b.getHigh() >= ma) {
                    // 用均线作为成交价（若开盘已高于均线，取开盘价更现实）
                    double px = b.getOpen() >= ma ? b.getOpen() : ma;
                    return pct(px, e.entry);
                }
            } else if (b.getHigh() >= target) {
                double px = b.getOpen() >= target ? b.getOpen() : target;
                return pct(px, e.entry);
            }
        }
        // 到期：最后一根收盘出场
        return pct(bars.get(last).getClose(), e.entry);
    }

    private double stopPrice(String sl, Entry e) {
        switch (sl) {
            case "LOW0.5":
                return e.signalLow * (1 - LOW_BUFFER);
            case "ATR1.5":
                return Double.isNaN(e.atr) ? Double.NaN : e.entry - 1.5 * e.atr;
            case "ATR2":
                return Double.isNaN(e.atr) ? Double.NaN : e.entry - 2.0 * e.atr;
            case "PCT3":
                return e.entry * 0.97;
            case "PCT5":
                return e.entry * 0.95;
            case "PCT8":
                return e.entry * 0.92;
            default:
                return Double.NaN;
        }
    }

    private double targetPrice(String tp, double entry, double r) {
        switch (tp) {
            case "1.5R":
                return entry + 1.5 * r;
            case "2R":
                return entry + 2.0 * r;
            case "3R":
                return entry + 3.0 * r;
            case "4R":
                return entry + 4.0 * r;
            case "PCT3":
                return entry * 1.03;
            case "PCT5":
                return entry * 1.05;
            case "PCT8":
                return entry * 1.08;
            case "PCT10":
                return entry * 1.10;
            case "PCT15":
                return entry * 1.15;
            default:
                return Double.NaN;
        }
    }

    private static double pct(double exit, double entry) {
        return (exit - entry) / entry * 100.0;
    }

    /** Wilder ATR；前 period 根不足时填 NaN。 */
    private static double[] atr(List<BarIndicator> bars, int period) {
        int n = bars.size();
        double[] out = new double[n];
        if (n == 0) {
            return out;
        }
        double[] tr = new double[n];
        tr[0] = bars.get(0).getHigh() - bars.get(0).getLow();
        for (int i = 1; i < n; i++) {
            double high = bars.get(i).getHigh();
            double low = bars.get(i).getLow();
            double prevClose = bars.get(i - 1).getClose();
            tr[i] = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        }
        for (int i = 0; i < n; i++) {
            out[i] = Double.NaN;
        }
        if (n >= period) {
            double sum = 0;
            for (int i = 0; i < period; i++) sum += tr[i];
            out[period - 1] = sum / period;
            for (int i = period; i < n; i++) {
                out[i] = (out[i - 1] * (period - 1) + tr[i]) / period;
            }
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
