package com.stock.signal;

import com.stock.config.StrategyProperties;
import com.stock.indicator.BarIndicator;
import com.stock.indicator.DivergenceDetector;
import com.stock.indicator.IndicatorSeries;
import com.stock.indicator.Indicators;
import com.stock.indicator.TdSignal;
import com.stock.scan.TradeCalendar;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 信号引擎：低9抄底 / 高9逃顶 组合判定（加权打分）。
 */
@Component
public class SignalEngine {

    private final StrategyProperties props;
    private final TradeCalendar tradeCalendar;

    public SignalEngine(StrategyProperties props, TradeCalendar tradeCalendar) {
        this.props = props;
        this.tradeCalendar = tradeCalendar;
    }

    /**
     * 对序列的最新一根K线进行评估（受 comboMode/scoreThreshold 过滤）。
     */
    public Optional<SignalResult> evaluate(IndicatorSeries series) {
        if (series == null || series.isEmpty()) {
            return Optional.empty();
        }
        return evaluateAt(series, series.size() - 1);
    }

    /**
     * 对序列的第 idx 根K线进行评估（受 comboMode/scoreThreshold 过滤）。
     */
    public Optional<SignalResult> evaluateAt(IndicatorSeries series, int idx) {
        return composeAt(series, idx).filter(this::passes);
    }

    /**
     * 仅按九转方向（低9/高9）组装打分结果，不做阈值过滤。
     * 回测使用，由调用方自行按分数筛选。
     */
    public Optional<SignalResult> composeAt(IndicatorSeries series, int idx) {
        if (series == null || series.isEmpty() || idx < 0 || idx >= series.size()) {
            return Optional.empty();
        }
        BarIndicator bar = series.getBars().get(idx);
        TdSignal td = bar.getTdSignal();
        if (td == TdSignal.LOW_9) {
            return Optional.of(composeBuy(series, bar, idx));
        } else if (td == TdSignal.HIGH_9) {
            return Optional.of(composeSell(series, bar, idx));
        }
        return Optional.empty();
    }

    private SignalResult composeBuy(IndicatorSeries s, BarIndicator bar, int idx) {
        List<SignalFactor> optional = new ArrayList<>();
        if (DivergenceDetector.bullish(trunc(s.getLow(), idx), trunc(s.getDif(), idx),
                props.getDivergence().getWindow(), props.getDivergence().getSwingK())) {
            optional.add(SignalFactor.MACD_BULL_DIV);
        }
        if (volumeShrink(s.getVolume(), idx)) {
            optional.add(SignalFactor.VOL_SHRINK);
        }
        if (maSupport(bar)) {
            optional.add(SignalFactor.MA_SUPPORT);
        }
        SignalResult r = build(s.getCode(), bar, SignalType.BUY_LOW9, SignalFactor.TD_LOW9, optional);
        fillBuyPlan(r, bar);
        return r;
    }

    /** 低9买入交易计划：ATR×倍数止损 / 回 MA20 止盈 / 最多持有 N 个交易日（参数来自回测优选）。 */
    private void fillBuyPlan(SignalResult r, BarIndicator bar) {
        StrategyProperties.TradePlan tp = props.getTradePlan();
        double entry = bar.getClose();
        r.setEntryPrice(round(entry));

        Double atr = bar.getAtr();
        double stop = (atr != null && atr > 0)
                ? entry - tp.getAtrMult() * atr
                : entry * (1 - tp.getFallbackSlPct() / 100.0);
        r.setStopPrice(round(stop));

        Double ma20 = bar.getMa20();
        double target = (ma20 != null && ma20 > entry)
                ? ma20
                : entry * (1 + tp.getFallbackTpPct() / 100.0);
        r.setTargetPrice(round(target));

        r.setLatestExitDate(tradeCalendar.plusTradingDays(bar.getTradeDate(), tp.getHoldTradingDays()));

        r.getDetail().put("entryPrice", r.getEntryPrice());
        r.getDetail().put("stopPrice", r.getStopPrice());
        r.getDetail().put("targetPrice", r.getTargetPrice());
        r.getDetail().put("latestExitDate", String.valueOf(r.getLatestExitDate()));
    }

    private static double round(double v) {
        return Math.round(v * 1e8d) / 1e8d;
    }

    private SignalResult composeSell(IndicatorSeries s, BarIndicator bar, int idx) {
        List<SignalFactor> optional = new ArrayList<>();
        if (DivergenceDetector.bearish(trunc(s.getHigh(), idx), trunc(s.getDif(), idx),
                props.getDivergence().getWindow(), props.getDivergence().getSwingK())) {
            optional.add(SignalFactor.MACD_BEAR_DIV);
        }
        if (volumeSurgeStall(s.getVolume(), bar, idx)) {
            optional.add(SignalFactor.VOL_SURGE_STALL);
        }
        if (maFar(bar)) {
            optional.add(SignalFactor.MA_FAR);
        }
        return build(s.getCode(), bar, SignalType.SELL_HIGH9, SignalFactor.TD_HIGH9, optional);
    }

    private boolean passes(SignalResult r) {
        return "STRICT".equalsIgnoreCase(props.getComboMode())
                ? r.getScore() == r.getMaxScore()
                : r.getScore() >= props.getScoreThreshold();
    }

    /** 截取 [0, idx] 子数组，使背离检测只看截至当前K线的历史。 */
    private static double[] trunc(double[] a, int idx) {
        if (a == null) {
            return new double[0];
        }
        int len = idx + 1;
        return len >= a.length ? a : Arrays.copyOfRange(a, 0, len);
    }

    private SignalResult build(String code, BarIndicator bar, SignalType type,
                               SignalFactor required, List<SignalFactor> optional) {
        int score = optional.size();
        int maxScore = 3;
        SignalResult r = new SignalResult();
        r.setCode(code);
        r.setTradeDate(bar.getTradeDate());
        r.setType(type);
        r.setScore(score);
        r.setMaxScore(maxScore);
        r.setStrong(score == maxScore);
        r.getFactors().add(required);
        r.getFactors().addAll(optional);
        r.getDetail().put("close", bar.getClose());
        r.getDetail().put("changePct", bar.getChangePct());
        r.getDetail().put("ma20", bar.getMa20());
        r.getDetail().put("ma60", bar.getMa60());
        r.getDetail().put("bias20", bar.getBias20());
        r.getDetail().put("bias60", bar.getBias60());
        r.getDetail().put("dif", bar.getDif());
        r.getDetail().put("dea", bar.getDea());
        r.getDetail().put("macd", bar.getMacd());
        r.getDetail().put("volume", bar.getVolume());
        r.getDetail().put("volMa20", bar.getVolMa20());
        return r;
    }

    /** 成交量持续缩量：近段均量 < 前段均量，且当日量 < 量能均线。 */
    private boolean volumeShrink(double[] vol, int idx) {
        int n = props.getVolume().getShrinkDays();
        int maP = props.getVolume().getMaPeriod();
        if (idx < 2 * n - 1 || idx < maP - 1) {
            return false;
        }
        double meanRecent = Indicators.mean(vol, idx - n + 1, idx);
        double meanPrev = Indicators.mean(vol, idx - 2 * n + 1, idx - n);
        double volMa = Indicators.mean(vol, idx - maP + 1, idx);
        return meanRecent < meanPrev && vol[idx] < volMa;
    }

    /** 放量滞涨：当日量 >= 量能均线 * 倍数，且涨幅 <= 阈值。 */
    private boolean volumeSurgeStall(double[] vol, BarIndicator last, int idx) {
        int maP = props.getVolume().getMaPeriod();
        if (idx < maP - 1) {
            return false;
        }
        double volMa = Indicators.mean(vol, idx - maP + 1, idx);
        double change = last.getChangePct() != null ? last.getChangePct() : 0d;
        boolean surge = volMa > 0 && vol[idx] >= volMa * props.getVolume().getSurgeRatio();
        boolean stall = change <= props.getVolume().getStallChangePct();
        return surge && stall;
    }

    /** 回踩均线支撑：最低价触及目标均线(容差内)且收盘守住该均线。 */
    private boolean maSupport(BarIndicator b) {
        double tol = props.getMaSupport().getTolerance();
        for (int period : props.getMaSupport().getPeriods()) {
            Double ma = maOf(b, period);
            if (ma == null || ma == 0) {
                continue;
            }
            boolean lowTouch = b.getLow() <= ma * (1 + tol);
            boolean holdAbove = b.getClose() >= ma;
            boolean near = (b.getClose() - ma) / ma <= tol;
            if (lowTouch && holdAbove && near) {
                return true;
            }
        }
        return false;
    }

    /** 股价远离均线：BIAS 超过阈值。 */
    private boolean maFar(BarIndicator b) {
        Double bias20 = b.getBias20();
        Double bias60 = b.getBias60();
        boolean far20 = bias20 != null && bias20 >= props.getBias().getMa20Far();
        boolean far60 = bias60 != null && bias60 >= props.getBias().getMa60Far();
        return far20 || far60;
    }

    private Double maOf(BarIndicator b, int period) {
        switch (period) {
            case 5:
                return b.getMa5();
            case 10:
                return b.getMa10();
            case 20:
                return b.getMa20();
            case 60:
                return b.getMa60();
            default:
                return null;
        }
    }
}
