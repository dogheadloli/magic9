package com.stock.indicator;

import lombok.Getter;

import java.util.List;

/**
 * 指标序列：既含逐根快照（绘图/查询），也含原始 double 数组（信号引擎做背离/量能计算）。
 */
@Getter
public class IndicatorSeries {
    private final String code;
    private final List<BarIndicator> bars;
    private final double[] close;
    private final double[] high;
    private final double[] low;
    private final double[] volume;
    private final double[] dif;

    public IndicatorSeries(String code, List<BarIndicator> bars,
                           double[] close, double[] high, double[] low,
                           double[] volume, double[] dif) {
        this.code = code;
        this.bars = bars;
        this.close = close;
        this.high = high;
        this.low = low;
        this.volume = volume;
        this.dif = dif;
    }

    public int size() {
        return bars.size();
    }

    public boolean isEmpty() {
        return bars.isEmpty();
    }

    public BarIndicator last() {
        return bars.get(bars.size() - 1);
    }
}
