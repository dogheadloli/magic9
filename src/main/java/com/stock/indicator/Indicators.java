package com.stock.indicator;

/**
 * 基础指标计算工具（内部使用 double，存储时再转 BigDecimal）。
 */
public final class Indicators {

    private Indicators() {
    }

    /**
     * 指数移动平均 EMA。ema[0] = v[0] 作为种子，长序列足够收敛。
     */
    public static double[] ema(double[] v, int period) {
        double[] e = new double[v.length];
        if (v.length == 0) {
            return e;
        }
        double alpha = 2.0 / (period + 1);
        e[0] = v[0];
        for (int i = 1; i < v.length; i++) {
            e[i] = v[i] * alpha + e[i - 1] * (1 - alpha);
        }
        return e;
    }

    /**
     * 简单移动平均 SMA。数据不足的位置返回 NaN。
     */
    public static double[] sma(double[] v, int period) {
        double[] s = new double[v.length];
        double sum = 0;
        for (int i = 0; i < v.length; i++) {
            sum += v[i];
            if (i >= period) {
                sum -= v[i - period];
            }
            s[i] = (i >= period - 1) ? sum / period : Double.NaN;
        }
        return s;
    }

    /**
     * 区间均值 [from, to]（含端点）。
     */
    public static double mean(double[] v, int from, int to) {
        double sum = 0;
        int cnt = 0;
        for (int i = from; i <= to && i < v.length; i++) {
            if (i >= 0) {
                sum += v[i];
                cnt++;
            }
        }
        return cnt == 0 ? Double.NaN : sum / cnt;
    }
}
