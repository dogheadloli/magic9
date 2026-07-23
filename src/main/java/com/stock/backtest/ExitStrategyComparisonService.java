package com.stock.backtest;

import com.stock.config.StrategyProperties;
import com.stock.domain.StockPool;
import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import com.stock.indicator.IndicatorService;
import com.stock.repository.StockPoolRepository;
import com.stock.signal.SignalEngine;
import com.stock.signal.SignalResult;
import com.stock.signal.SignalType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 对数据库全部低9信号比较两种退出方式。
 */
@Service
public class ExitStrategyComparisonService {

    private static final double TRAILING_ATR_MULT = 2.0;

    private final StockPoolRepository poolRepository;
    private final IndicatorService indicatorService;
    private final StrategyProperties strategyProperties;
    private final SignalEngine signalEngine;

    public ExitStrategyComparisonService(StockPoolRepository poolRepository,
                                         IndicatorService indicatorService,
                                         StrategyProperties strategyProperties,
                                         SignalEngine signalEngine) {
        this.poolRepository = poolRepository;
        this.indicatorService = indicatorService;
        this.strategyProperties = strategyProperties;
        this.signalEngine = signalEngine;
    }

    public ExitStrategyComparison run() {
        ExitStrategyComparison report = new ExitStrategyComparison();
        report.setFullRule("触及动态MA20全仓止盈；ATR×1.5止损；最多持有15个交易日");
        report.setTrailingRule("触及动态MA20卖出50%；剩余50%以不低于进场价且为最高收盘价-2×ATR(14)的止损逐日上移；最多持有15个交易日");

        List<Double> fullReturns = new ArrayList<>();
        List<Double> trailingReturns = new ArrayList<>();
        double deltaSum = 0;
        int matched = 0;
        int better = 0;
        int totalSignals = 0;

        for (StockPool stock : poolRepository.findByEnabledTrue()) {
            IndicatorSeries series = indicatorService.compute(stock.getCode());
            if (series == null || series.isEmpty()) {
                continue;
            }
            List<BarIndicator> bars = series.getBars();
            for (int idx = 0; idx < series.size(); idx++) {
                Optional<SignalResult> candidate = signalEngine.composeAt(series, idx);
                if (!candidate.isPresent() || candidate.get().getType() != SignalType.BUY_LOW9) {
                    continue;
                }
                SignalResult signal = candidate.get();
                totalSignals++;
                double entry = bars.get(idx).getClose();
                double stop = stopPrice(signal, bars.get(idx), entry);
                double fallbackTarget = targetPrice(signal, bars.get(idx), entry);

                ExitStrategyComparison.Result full =
                        simulateFull(bars, idx, entry, stop, fallbackTarget);
                ExitStrategyComparison.Result trailing =
                        simulateTrailing(bars, idx, entry, stop, fallbackTarget);

                ExitStrategyComparison.TradeComparison trade =
                        new ExitStrategyComparison.TradeComparison();
                trade.setCode(stock.getCode());
                trade.setName(stock.getName());
                trade.setSignalDate(bars.get(idx).getTradeDate());
                trade.setEntryPrice(round(entry));
                trade.setStopPrice(round(stop));
                trade.setInitialRiskPct(round((entry - stop) / entry * 100));
                trade.setScore(signal.getScore());
                trade.setMaxScore(signal.getMaxScore());
                trade.setStrong(signal.isStrong());
                trade.setFactors(signal.getFactorLabels());
                trade.setFactorCodes(signal.getFactors().stream().map(Enum::name).collect(Collectors.toList()));
                trade.setFullMa20(full);
                trade.setHalfTrailing(trailing);
                if (full.getReturnPct() != null && trailing.getReturnPct() != null) {
                    double delta = round(trailing.getReturnPct() - full.getReturnPct());
                    trade.setReturnDeltaPct(delta);
                    deltaSum += delta;
                    matched++;
                    if (delta > 0) {
                        better++;
                    }
                }
                report.getTrades().add(trade);
                if (full.getReturnPct() != null) {
                    fullReturns.add(full.getReturnPct());
                }
                if (trailing.getReturnPct() != null) {
                    trailingReturns.add(trailing.getReturnPct());
                }
            }
        }

        report.setTotalSignals(totalSignals);
        report.setFullMa20(stats(fullReturns, totalSignals));
        report.setHalfTrailing(stats(trailingReturns, totalSignals));
        report.setMatchedClosed(matched);
        report.setTrailingBetter(better);
        report.setAvgReturnDeltaPct(matched > 0 ? round(deltaSum / matched) : null);
        report.getTrades().sort((a, b) -> b.getSignalDate().compareTo(a.getSignalDate()));
        return report;
    }

    private ExitStrategyComparison.Result simulateFull(List<BarIndicator> bars, int idx,
                                                       double entry, double stop, double fallbackTarget) {
        int hold = strategyProperties.getTradePlan().getHoldTradingDays();
        int available = bars.size() - 1 - idx;
        int last = Math.min(idx + hold, bars.size() - 1);
        for (int k = idx + 1; k <= last; k++) {
            BarIndicator bar = bars.get(k);
            if (bar.getLow() <= stop) {
                return closed("SL", bar, stop, entry, k - idx);
            }
            double target = dynamicTarget(bar, fallbackTarget);
            if (bar.getHigh() >= target) {
                double exit = bar.getOpen() >= target ? bar.getOpen() : target;
                return closed("TP", bar, exit, entry, k - idx);
            }
        }
        if (available >= hold) {
            BarIndicator bar = bars.get(idx + hold);
            return closed("TIME", bar, bar.getClose(), entry, hold);
        }
        return open("OPEN", available);
    }

    private ExitStrategyComparison.Result simulateTrailing(List<BarIndicator> bars, int idx,
                                                           double entry, double stop, double fallbackTarget) {
        int hold = strategyProperties.getTradePlan().getHoldTradingDays();
        int available = bars.size() - 1 - idx;
        int last = Math.min(idx + hold, bars.size() - 1);
        boolean halfSold = false;
        double firstExit = 0;
        double highestClose = entry;
        double trailingStop = entry;
        BarIndicator firstExitBar = null;

        for (int k = idx + 1; k <= last; k++) {
            BarIndicator bar = bars.get(k);
            if (!halfSold) {
                if (bar.getLow() <= stop) {
                    return closed("SL", bar, stop, entry, k - idx);
                }
                double target = dynamicTarget(bar, fallbackTarget);
                if (bar.getHigh() >= target) {
                    firstExit = bar.getOpen() >= target ? bar.getOpen() : target;
                    firstExitBar = bar;
                    halfSold = true;
                    highestClose = Math.max(entry, bar.getClose());
                    trailingStop = nextTrailingStop(entry, highestClose, bar.getAtr());
                }
            } else {
                if (bar.getLow() <= trailingStop) {
                    return combined("TRAIL", bar, trailingStop, entry, k - idx,
                            firstExitBar, firstExit);
                }
                highestClose = Math.max(highestClose, bar.getClose());
                trailingStop = Math.max(trailingStop,
                        nextTrailingStop(entry, highestClose, bar.getAtr()));
            }
        }

        if (available >= hold) {
            BarIndicator expiry = bars.get(idx + hold);
            if (halfSold) {
                return combined("TIME", expiry, expiry.getClose(), entry, hold,
                        firstExitBar, firstExit);
            }
            return closed("TIME", expiry, expiry.getClose(), entry, hold);
        }
        ExitStrategyComparison.Result open = open(halfSold ? "OPEN_HALF" : "OPEN", available);
        if (halfSold) {
            open.setFirstHalfExitDate(firstExitBar.getTradeDate());
            open.setFirstHalfExitPrice(round(firstExit));
        }
        return open;
    }

    private ExitStrategyComparison.Result combined(String status, BarIndicator finalBar,
                                                   double finalExit, double entry, int holdDays,
                                                   BarIndicator firstBar, double firstExit) {
        ExitStrategyComparison.Result result = new ExitStrategyComparison.Result();
        result.setStatus(status);
        result.setExitDate(finalBar.getTradeDate());
        result.setExitPrice(round(finalExit));
        result.setHoldDays(holdDays);
        result.setFirstHalfExitDate(firstBar.getTradeDate());
        result.setFirstHalfExitPrice(round(firstExit));
        double firstReturn = (firstExit - entry) / entry * 100;
        double finalReturn = (finalExit - entry) / entry * 100;
        result.setReturnPct(round((firstReturn + finalReturn) / 2));
        return result;
    }

    private ExitStrategyComparison.Result closed(String status, BarIndicator bar, double exit,
                                                 double entry, int holdDays) {
        ExitStrategyComparison.Result result = new ExitStrategyComparison.Result();
        result.setStatus(status);
        result.setExitDate(bar.getTradeDate());
        result.setExitPrice(round(exit));
        result.setReturnPct(round((exit - entry) / entry * 100));
        result.setHoldDays(holdDays);
        return result;
    }

    private ExitStrategyComparison.Result open(String status, int holdDays) {
        ExitStrategyComparison.Result result = new ExitStrategyComparison.Result();
        result.setStatus(status);
        result.setHoldDays(Math.max(0, holdDays));
        return result;
    }

    private ExitStrategyComparison.StrategyStats stats(List<Double> returns, int evaluable) {
        ExitStrategyComparison.StrategyStats stats = new ExitStrategyComparison.StrategyStats();
        stats.setClosed(returns.size());
        stats.setOpen(Math.max(0, evaluable - returns.size()));
        if (returns.isEmpty()) {
            return stats;
        }
        int wins = 0;
        double sum = 0, grossWin = 0, grossLoss = 0;
        double equity = 1, peak = 1, maxDrawdown = 0;
        for (double value : returns) {
            sum += value;
            if (value > 0) {
                wins++;
                grossWin += value;
            } else {
                grossLoss += value;
            }
            equity *= 1 + value / 100d;
            peak = Math.max(peak, equity);
            maxDrawdown = Math.max(maxDrawdown, (peak - equity) / peak * 100);
        }
        List<Double> sorted = new ArrayList<>(returns);
        Collections.sort(sorted);
        double median = sorted.size() % 2 == 1
                ? sorted.get(sorted.size() / 2)
                : (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2;
        stats.setWins(wins);
        stats.setWinRatePct(round(wins * 100d / returns.size()));
        stats.setAvgReturnPct(round(sum / returns.size()));
        stats.setMedianReturnPct(round(median));
        stats.setProfitFactor(grossLoss < 0 ? round(grossWin / -grossLoss) : null);
        stats.setCumulativeReturnPct(round((equity - 1) * 100));
        stats.setMaxDrawdownPct(round(maxDrawdown));
        return stats;
    }

    private double stopPrice(SignalResult signal, BarIndicator bar, double entry) {
        if (positive(signal.getStopPrice())) {
            return signal.getStopPrice();
        }
        if (positive(bar.getAtr())) {
            return entry - strategyProperties.getTradePlan().getAtrMult() * bar.getAtr();
        }
        return entry * (1 - strategyProperties.getTradePlan().getFallbackSlPct() / 100d);
    }

    private double targetPrice(SignalResult signal, BarIndicator bar, double entry) {
        if (positive(signal.getTargetPrice())) {
            return signal.getTargetPrice();
        }
        if (positive(bar.getMa20()) && bar.getMa20() > entry) {
            return bar.getMa20();
        }
        return entry * (1 + strategyProperties.getTradePlan().getFallbackTpPct() / 100d);
    }

    private double dynamicTarget(BarIndicator bar, double fallback) {
        return positive(bar.getMa20()) ? bar.getMa20() : fallback;
    }

    private double nextTrailingStop(double entry, double highestClose, Double atr) {
        if (!positive(atr)) {
            return entry;
        }
        return Math.max(entry, highestClose - TRAILING_ATR_MULT * atr);
    }

    private boolean positive(Double value) {
        return value != null && value > 0;
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
