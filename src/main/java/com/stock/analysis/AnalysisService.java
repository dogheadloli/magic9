package com.stock.analysis;

import com.stock.config.StrategyProperties;
import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import com.stock.indicator.IndicatorService;
import com.stock.repository.StockPoolRepository;
import com.stock.scan.RealtimeScanService;
import com.stock.signal.SignalEngine;
import com.stock.signal.SignalResult;
import com.stock.signal.SignalType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 买卖分析：当下决策（实时信号 + 计划 + 距离）与历史逐笔复盘（回测优选规则）。
 */
@Service
public class AnalysisService {

    private final IndicatorService indicatorService;
    private final SignalEngine signalEngine;
    private final RealtimeScanService realtimeScanService;
    private final StockPoolRepository poolRepository;
    private final StrategyProperties props;

    public AnalysisService(IndicatorService indicatorService, SignalEngine signalEngine,
                           RealtimeScanService realtimeScanService, StockPoolRepository poolRepository,
                           StrategyProperties props) {
        this.indicatorService = indicatorService;
        this.signalEngine = signalEngine;
        this.realtimeScanService = realtimeScanService;
        this.poolRepository = poolRepository;
        this.props = props;
    }

    private String nameOf(String code) {
        return poolRepository.findByCode(code).map(p -> p.getName()).orElse(code);
    }

    /** 当下决策：优先看最新一根；无信号则回落到最近一次历史信号。 */
    public CurrentDecision current(String code) {
        CurrentDecision d = new CurrentDecision();
        d.setCode(code);
        d.setName(nameOf(code));

        IndicatorSeries series = realtimeScanService.computeRealtimeSeries(code);
        if (series == null || series.isEmpty()) {
            d.setHasSignal(false);
            d.setAdvice("暂无K线数据");
            return d;
        }
        int lastIdx = series.size() - 1;
        BarIndicator lastBar = series.getBars().get(lastIdx);
        d.setAsOf(lastBar.getTradeDate());
        d.setPrice(lastBar.getClose());

        // 最新一根是否触发
        Optional<SignalResult> latest = signalEngine.composeAt(series, lastIdx);
        boolean fresh = latest.isPresent();
        Optional<SignalResult> chosen = latest;
        // 回落：向前最多 60 根找最近一次信号
        if (!chosen.isPresent()) {
            for (int j = lastIdx - 1; j >= 0 && j >= lastIdx - 60; j--) {
                Optional<SignalResult> r = signalEngine.composeAt(series, j);
                if (r.isPresent()) {
                    chosen = r;
                    break;
                }
            }
        }

        if (!chosen.isPresent()) {
            d.setHasSignal(false);
            d.setAdvice("近 60 个交易日内无信号");
            return d;
        }

        SignalResult s = chosen.get();
        d.setHasSignal(true);
        d.setFresh(fresh);
        d.setSignalType(s.getType().name());
        d.setSignalDate(s.getTradeDate());
        d.setScore(s.getScore());
        d.setMaxScore(s.getMaxScore());
        d.setFactors(s.getFactorLabels());

        if (s.getType() == SignalType.BUY_LOW9) {
            d.setEntryPrice(s.getEntryPrice());
            d.setStopPrice(s.getStopPrice());
            d.setTargetPrice(s.getTargetPrice());
            d.setLatestExitDate(s.getLatestExitDate());
            double price = lastBar.getClose();
            if (s.getStopPrice() != null && price > 0) {
                d.setDistToStopPct(round((price - s.getStopPrice()) / price * 100));
                double hardStop = hardStopPrice(s.getStopPrice());
                d.setBrokeStop(price <= hardStop);
            }
            if (s.getTargetPrice() != null && price > 0) {
                d.setDistToTargetPct(round((s.getTargetPrice() - price) / price * 100));
            }
            d.setExpired(s.getLatestExitDate() != null && LocalDate.now().isAfter(s.getLatestExitDate()));
            d.setAdvice(buyAdvice(d, fresh));
        } else {
            d.setAdvice(fresh ? "今日触发高9逃顶信号，注意减仓/止盈" : "最近一次为高9逃顶信号");
        }
        return d;
    }

    private String buyAdvice(CurrentDecision d, boolean fresh) {
        StringBuilder sb = new StringBuilder();
        sb.append(fresh ? "今日触发低9买点。" : "最近一次低9买点（" + d.getSignalDate() + "）。");
        if (d.isBrokeStop()) {
            sb.append("现价已跌破止损，计划失效。");
        } else if (d.isExpired()) {
            sb.append("已过最晚了结日，计划失效。");
        } else {
            if (d.getDistToTargetPct() != null) {
                sb.append("距目标约 ").append(d.getDistToTargetPct()).append("%，");
            }
            if (d.getDistToStopPct() != null) {
                sb.append("距止损约 ").append(d.getDistToStopPct()).append("%。");
            }
        }
        return sb.toString();
    }

    /**
     * 历史逐笔复盘：日K无法还原连续一小时软止损，只模拟硬止损、回MA20止盈和到期退出。
     */
    public HistoryReport history(String code) {
        HistoryReport rep = new HistoryReport();
        rep.setCode(code);
        rep.setName(nameOf(code));

        IndicatorSeries series = indicatorService.compute(code);
        if (series == null || series.isEmpty()) {
            return rep;
        }
        List<BarIndicator> bars = series.getBars();
        int n = bars.size();
        int hold = props.getTradePlan().getHoldTradingDays();

        double sumRet = 0, sumWin = 0, sumLoss = 0;
        int wins = 0, losses = 0, closed = 0, open = 0;

        for (int i = 0; i < n; i++) {
            Optional<SignalResult> r = signalEngine.composeAt(series, i);
            if (!r.isPresent() || r.get().getType() != SignalType.BUY_LOW9) {
                continue;
            }
            SignalResult s = r.get();
            TradeDetail t = new TradeDetail();
            t.setEntryDate(bars.get(i).getTradeDate());
            t.setEntryPrice(bars.get(i).getClose());
            t.setStopPrice(s.getStopPrice());
            t.setTargetPrice(s.getTargetPrice());
            t.setScore(s.getScore());
            t.setFactors(s.getFactorLabels());

            simulate(bars, i, hold, t);
            rep.getTrades().add(t);

            if ("OPEN".equals(t.getReason())) {
                open++;
            } else {
                closed++;
                double ret = t.getReturnPct() != null ? t.getReturnPct() : 0;
                sumRet += ret;
                if (ret > 0) {
                    wins++;
                    sumWin += ret;
                } else {
                    losses++;
                    sumLoss += ret;
                }
            }
        }

        rep.setCount(closed);
        rep.setOpenCount(open);
        rep.setWins(wins);
        if (closed > 0) {
            rep.setWinRatePct(round(wins * 100.0 / closed));
            rep.setAvgReturnPct(round(sumRet / closed));
            rep.setTotalReturnPct(round(sumRet));
            rep.setAvgWinPct(wins > 0 ? round(sumWin / wins) : 0);
            rep.setAvgLossPct(losses > 0 ? round(sumLoss / losses) : 0);
            rep.setProfitFactor(sumLoss < 0 ? round(sumWin / -sumLoss) : (sumWin > 0 ? 99.99 : 0));
        }
        double sumHold = 0;
        int holdN = 0;
        for (TradeDetail t : rep.getTrades()) {
            if (t.getHoldDays() != null) {
                sumHold += t.getHoldDays();
                holdN++;
            }
        }
        rep.setAvgHoldDays(holdN > 0 ? round(sumHold / holdN) : 0);

        // 最近的在最前面
        rep.getTrades().sort((a, b) -> b.getEntryDate().compareTo(a.getEntryDate()));
        return rep;
    }

    /**
     * 对数据库中某个低9信号做单笔复盘：在完整指标序列中定位该交易日，
     * 用回测优选规则（ATR×1.5止损/回MA20止盈/最多持N日）模拟结果。
     * 找不到该交易日返回 null。
     */
    public TradeDetail reviewSignal(IndicatorSeries series, LocalDate signalDate) {
        if (series == null || series.isEmpty() || signalDate == null) {
            return null;
        }
        List<BarIndicator> bars = series.getBars();
        int idx = -1;
        for (int i = 0; i < bars.size(); i++) {
            if (signalDate.equals(bars.get(i).getTradeDate())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return null;
        }
        TradeDetail t = new TradeDetail();
        t.setEntryDate(bars.get(idx).getTradeDate());
        t.setEntryPrice(round(bars.get(idx).getClose()));
        Optional<SignalResult> r = signalEngine.composeAt(series, idx);
        if (r.isPresent()) {
            t.setStopPrice(r.get().getStopPrice());
            t.setTargetPrice(r.get().getTargetPrice());
            t.setScore(r.get().getScore());
            t.setFactors(r.get().getFactorLabels());
        }
        simulate(bars, idx, props.getTradePlan().getHoldTradingDays(), t);
        return t;
    }

    /** 进场后 1..hold 根内：先判硬止损，再判是否回到 MA20 止盈；到期按收盘了结。 */
    private void simulate(List<BarIndicator> bars, int idx, int hold, TradeDetail t) {
        int n = bars.size();
        double entry = bars.get(idx).getClose();
        Double stop = t.getStopPrice();
        int available = n - 1 - idx;
        if (available <= 0) {
            t.setReason("OPEN");
            t.setHoldDays(0);
            return;
        }
        int last = Math.min(idx + hold, n - 1);
        for (int k = idx + 1; k <= last; k++) {
            BarIndicator b = bars.get(k);
            double hardStop = stop == null ? Double.NaN : hardStopPrice(stop);
            if (stop != null && b.getLow() <= hardStop) {
                double exit = b.getOpen() <= hardStop ? b.getOpen() : hardStop;
                fill(t, b.getTradeDate(), exit, "SL", entry, k - idx);
                return;
            }
            Double ma20 = b.getMa20();
            if (ma20 != null && ma20 > 0 && b.getHigh() >= ma20) {
                double px = b.getOpen() >= ma20 ? b.getOpen() : ma20;
                fill(t, b.getTradeDate(), px, "TP", entry, k - idx);
                return;
            }
        }
        if (available < hold) {
            t.setReason("OPEN");
            t.setHoldDays(available);
            return;
        }
        BarIndicator lb = bars.get(last);
        fill(t, lb.getTradeDate(), lb.getClose(), "TIME", entry, last - idx);
    }

    private void fill(TradeDetail t, LocalDate exitDate, double exitPrice, String reason,
                      double entry, int holdDays) {
        t.setExitDate(exitDate);
        t.setExitPrice(round(exitPrice));
        t.setReason(reason);
        t.setReturnPct(round((exitPrice - entry) / entry * 100));
        t.setHoldDays(holdDays);
    }

    private static double round(double v) {
        return Math.round(v * 100d) / 100d;
    }

    private double hardStopPrice(double stop) {
        return stop * (1 - props.getTradePlan().getHardStopBelowPct() / 100d);
    }
}
