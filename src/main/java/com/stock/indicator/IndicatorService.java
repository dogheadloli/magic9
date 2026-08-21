package com.stock.indicator;

import com.stock.config.StrategyProperties;
import com.stock.domain.KlineDaily;
import com.stock.repository.KlineDailyRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 由日K构建指标序列（TD9 / MACD / MA / 量能均线 / BIAS）。
 */
@Service
public class IndicatorService {

    private final KlineDailyRepository klineRepository;
    private final StrategyProperties props;

    public IndicatorService(KlineDailyRepository klineRepository, StrategyProperties props) {
        this.klineRepository = klineRepository;
        this.props = props;
    }

    public IndicatorSeries compute(String code) {
        List<KlineDaily> klines = klineRepository.findByCodeOrderByTradeDateAsc(code);
        return compute(code, klines);
    }

    /** 截至指定交易日（含）的指标序列，用于历史评估/调试。 */
    public IndicatorSeries computeAsOf(String code, java.time.LocalDate asOf) {
        List<KlineDaily> klines = new ArrayList<>();
        for (KlineDaily k : klineRepository.findByCodeOrderByTradeDateAsc(code)) {
            if (!k.getTradeDate().isAfter(asOf)) {
                klines.add(k);
            }
        }
        return compute(code, klines);
    }

    public IndicatorSeries compute(String code, List<KlineDaily> klines) {
        List<PriceBar> bars = new ArrayList<>(klines.size());
        for (KlineDaily k : klines) {
            PriceBar p = new PriceBar();
            p.setTradeDate(k.getTradeDate());
            p.setOpen(toD(k.getOpen()));
            p.setHigh(toD(k.getHigh()));
            p.setLow(toD(k.getLow()));
            p.setClose(toD(k.getClose()));
            p.setVolume(k.getVolume() != null ? k.getVolume() : 0d);
            p.setChangePct(k.getChangePct() != null ? k.getChangePct().doubleValue() : null);
            bars.add(p);
        }
        return computeFrom(code, bars);
    }

    /** 由通用 OHLC 序列计算指标（股票日K / 加密货币多周期）。 */
    public IndicatorSeries computeFrom(String code, List<PriceBar> inputs) {
        int n = inputs.size();
        double[] close = new double[n];
        double[] high = new double[n];
        double[] low = new double[n];
        double[] open = new double[n];
        double[] volume = new double[n];
        for (int i = 0; i < n; i++) {
            PriceBar k = inputs.get(i);
            close[i] = k.getClose();
            high[i] = k.getHigh();
            low[i] = k.getLow();
            open[i] = k.getOpen();
            volume[i] = k.getVolume();
        }

        Td9Calculator.Result td = Td9Calculator.compute(close, props.getTdSetupLength());
        MacdCalculator.Result macd = MacdCalculator.compute(close,
                props.getMacd().getFast(), props.getMacd().getSlow(), props.getMacd().getSignal());
        double[] ma5 = Indicators.sma(close, 5);
        double[] ma10 = Indicators.sma(close, 10);
        double[] ma20 = Indicators.sma(close, 20);
        double[] ma60 = Indicators.sma(close, 60);
        double[] volMa5 = Indicators.sma(volume, 5);
        double[] volMa20 = Indicators.sma(volume, props.getVolume().getMaPeriod());
        double[] atr = atr(high, low, close, props.getTradePlan().getAtrPeriod());

        List<BarIndicator> bars = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            PriceBar k = inputs.get(i);
            BarIndicator b = new BarIndicator();
            b.setTradeDate(k.getTradeDate());
            b.setOpenTime(k.getOpenTime());
            b.setOpenTimeMs(toEpochMs(k.getOpenTime()));
            b.setOpen(open[i]);
            b.setHigh(high[i]);
            b.setLow(low[i]);
            b.setClose(close[i]);
            b.setVolume(volume[i]);
            b.setChangePct(k.getChangePct());
            b.setTdBuySetup(td.buySetup[i]);
            b.setTdSellSetup(td.sellSetup[i]);
            b.setTdSignal(td.signal[i]);
            b.setDif(nz(macd.dif[i]));
            b.setDea(nz(macd.dea[i]));
            b.setMacd(nz(macd.hist[i]));
            b.setMa5(nz(ma5[i]));
            b.setMa10(nz(ma10[i]));
            b.setMa20(nz(ma20[i]));
            b.setMa60(nz(ma60[i]));
            b.setVolMa5(nz(volMa5[i]));
            b.setVolMa20(nz(volMa20[i]));
            b.setBias20(bias(close[i], ma20[i]));
            b.setBias60(bias(close[i], ma60[i]));
            b.setAtr(nz(atr[i]));
            bars.add(b);
        }
        return new IndicatorSeries(code, bars, close, high, low, volume, macd.dif);
    }

    /** Wilder ATR；前 period 根不足时为 NaN。 */
    private static double[] atr(double[] high, double[] low, double[] close, int period) {
        int n = close.length;
        double[] out = new double[n];
        java.util.Arrays.fill(out, Double.NaN);
        if (n == 0 || period <= 0) {
            return out;
        }
        double[] tr = new double[n];
        tr[0] = high[0] - low[0];
        for (int i = 1; i < n; i++) {
            double hl = high[i] - low[i];
            double hc = Math.abs(high[i] - close[i - 1]);
            double lc = Math.abs(low[i] - close[i - 1]);
            tr[i] = Math.max(hl, Math.max(hc, lc));
        }
        if (n >= period) {
            double sum = 0;
            for (int i = 0; i < period; i++) {
                sum += tr[i];
            }
            out[period - 1] = sum / period;
            for (int i = period; i < n; i++) {
                out[i] = (out[i - 1] * (period - 1) + tr[i]) / period;
            }
        }
        return out;
    }

    private static Long toEpochMs(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static double toD(java.math.BigDecimal v) {
        return v == null ? Double.NaN : v.doubleValue();
    }

    private static Double nz(double v) {
        return Double.isNaN(v) ? null : round(v);
    }

    private static Double bias(double close, double ma) {
        if (Double.isNaN(ma) || ma == 0) {
            return null;
        }
        return round((close - ma) / ma * 100);
    }

    private static double round(double v) {
        return Math.round(v * 10000d) / 10000d;
    }
}
