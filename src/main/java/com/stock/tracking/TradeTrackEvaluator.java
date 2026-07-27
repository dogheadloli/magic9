package com.stock.tracking;

import com.stock.config.StrategyProperties;
import com.stock.domain.SignalTradeTrack;
import com.stock.domain.TradeTrackStatus;
import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 低9交易计划状态计算器。
 *
 * 从信号后的下一根K线开始检查；硬止损为计划止损价下方指定比例。
 * 同一根同时触发硬止损/止盈时按保守原则先止损。
 * 未走满最大持有根数时必须保持 OPEN，不能用当前最后一根提前标记 TIME。
 */
@Component
public class TradeTrackEvaluator {

    private final StrategyProperties strategyProperties;

    public TradeTrackEvaluator(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    public TradeTrackEvaluation evaluate(SignalTradeTrack track, IndicatorSeries series) {
        return evaluate(track, series, true);
    }

    /** 历史数据修复专用：以每日收盘价是否跌破原止损价判断止损。 */
    public TradeTrackEvaluation evaluateWithCloseStop(SignalTradeTrack track, IndicatorSeries series) {
        return evaluate(track, series, true, true);
    }

    /**
     * @param allowTimeExpiry false 表示盘中检查，只允许 TP/SL；TIME 必须等收盘定稿后判断
     */
    public TradeTrackEvaluation evaluate(SignalTradeTrack track, IndicatorSeries series,
                                         boolean allowTimeExpiry) {
        return evaluate(track, series, allowTimeExpiry, false);
    }

    private TradeTrackEvaluation evaluate(SignalTradeTrack track, IndicatorSeries series,
                                          boolean allowTimeExpiry, boolean closeStop) {
        TradeTrackEvaluation result = new TradeTrackEvaluation();
        result.setEntryPrice(track.getEntryPrice());
        result.setStopPrice(track.getStopPrice());
        result.setTargetPrice(track.getTargetPrice());
        if (series == null || series.isEmpty()) {
            return result;
        }

        List<BarIndicator> bars = series.getBars();
        int idx = indexOf(bars, track);
        if (idx < 0) {
            return result;
        }

        double entry = positive(track.getEntryPrice())
                ? track.getEntryPrice() : bars.get(idx).getClose();
        result.setEntryPrice(round(entry));
        BarIndicator signalBar = bars.get(idx);
        Double stop = track.getStopPrice();
        if (!positive(stop)) {
            stop = positive(signalBar.getAtr())
                    ? entry - strategyProperties.getTradePlan().getAtrMult() * signalBar.getAtr()
                    : entry * (1 - strategyProperties.getTradePlan().getFallbackSlPct() / 100d);
        }
        result.setStopPrice(round(stop));

        Double fallbackTarget = track.getTargetPrice();
        if (!positive(fallbackTarget)) {
            fallbackTarget = positive(signalBar.getMa20()) && signalBar.getMa20() > entry
                    ? signalBar.getMa20()
                    : entry * (1 + strategyProperties.getTradePlan().getFallbackTpPct() / 100d);
        }
        result.setTargetPrice(round(fallbackTarget));

        int hold = strategyProperties.getTradePlan().getHoldTradingDays();
        int available = bars.size() - 1 - idx;
        int lastToCheck = Math.min(idx + hold, bars.size() - 1);

        for (int k = idx + 1; k <= lastToCheck; k++) {
            BarIndicator bar = bars.get(k);
            double stopThreshold = closeStop ? stop : stop * (1
                    - strategyProperties.getTradePlan().getHardStopBelowPct() / 100d);
            double triggerPrice = closeStop ? bar.getClose() : bar.getLow();
            if (triggerPrice <= stopThreshold) {
                double exit = closeStop ? bar.getClose()
                        : (bar.getOpen() <= stopThreshold ? bar.getOpen() : stopThreshold);
                close(result, TradeTrackStatus.SL, bar, exit, entry, k - idx);
                return result;
            }

            Double target = dynamicTarget(bar, fallbackTarget);
            if (positive(target) && bar.getHigh() >= target) {
                double exit = bar.getOpen() >= target ? bar.getOpen() : target;
                result.setTargetPrice(round(target));
                close(result, TradeTrackStatus.TP, bar, exit, entry, k - idx);
                return result;
            }
        }

        result.setHoldDays(Math.max(0, available));
        if (available >= hold && allowTimeExpiry) {
            BarIndicator expiry = bars.get(idx + hold);
            close(result, TradeTrackStatus.TIME, expiry, expiry.getClose(), entry, hold);
        }
        return result;
    }

    /**
     * 使用当前实时价维护软止损计时。监测中断、价格回到止损线或时间倒退时重新计时。
     */
    public TradeTrackEvaluation evaluateSoftStop(SignalTradeTrack track, BarIndicator currentBar,
                                                 LocalDateTime now) {
        TradeTrackEvaluation result = new TradeTrackEvaluation();
        result.setEntryPrice(track.getEntryPrice());
        result.setStopPrice(track.getStopPrice());
        result.setTargetPrice(track.getTargetPrice());
        result.setHoldDays(track.getHoldDays() == null ? 0 : track.getHoldDays());
        if (currentBar == null || now == null || !positive(track.getStopPrice())
                || !positive(currentBar.getClose())) {
            return result;
        }

        double currentPrice = currentBar.getClose();
        if (currentPrice >= track.getStopPrice()) {
            clearSoftStopTimer(track);
            return result;
        }

        LocalDateTime lastChecked = track.getSoftStopLastCheckedAt();
        long maxGap = strategyProperties.getTradePlan().getSoftStopMaxCheckGapMinutes();
        boolean interrupted = lastChecked == null || now.isBefore(lastChecked)
                || Duration.between(lastChecked, now).toMinutes() > maxGap;
        if (track.getSoftStopStartedAt() == null || interrupted) {
            track.setSoftStopStartedAt(now);
        }
        track.setSoftStopLastCheckedAt(now);

        long belowMinutes = Duration.between(track.getSoftStopStartedAt(), now).toMinutes();
        if (belowMinutes >= strategyProperties.getTradePlan().getSoftStopDurationMinutes()) {
            close(result, TradeTrackStatus.SL, currentBar, currentPrice,
                    track.getEntryPrice(), result.getHoldDays());
            clearSoftStopTimer(track);
        }
        return result;
    }

    public void clearSoftStopTimer(SignalTradeTrack track) {
        track.setSoftStopStartedAt(null);
        track.setSoftStopLastCheckedAt(null);
    }

    private int indexOf(List<BarIndicator> bars, SignalTradeTrack track) {
        for (int i = 0; i < bars.size(); i++) {
            if (track.getSignalDate().equals(bars.get(i).getTradeDate())) {
                return i;
            }
        }
        return -1;
    }

    private Double dynamicTarget(BarIndicator bar, Double fallbackTarget) {
        if (positive(bar.getMa20())) {
            return bar.getMa20();
        }
        return fallbackTarget;
    }

    private void close(TradeTrackEvaluation result, TradeTrackStatus status, BarIndicator bar,
                       double exitPrice, double entryPrice, int holdDays) {
        result.setStatus(status);
        result.setExitDate(bar.getTradeDate());
        result.setExitPrice(round(exitPrice));
        result.setReturnPct(round((exitPrice - entryPrice) / entryPrice * 100));
        result.setHoldDays(holdDays);
    }

    private boolean positive(Double value) {
        return value != null && value > 0;
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
