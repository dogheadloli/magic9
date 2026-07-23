package com.stock.indicator;

/**
 * MACD：DIF = EMA(fast) - EMA(slow)，DEA = EMA(DIF, signal)，柱 = 2 * (DIF - DEA)。
 */
public final class MacdCalculator {

    private MacdCalculator() {
    }

    public static class Result {
        public final double[] dif;
        public final double[] dea;
        public final double[] hist;

        Result(double[] dif, double[] dea, double[] hist) {
            this.dif = dif;
            this.dea = dea;
            this.hist = hist;
        }
    }

    public static Result compute(double[] close, int fast, int slow, int signal) {
        double[] emaFast = Indicators.ema(close, fast);
        double[] emaSlow = Indicators.ema(close, slow);
        int n = close.length;
        double[] dif = new double[n];
        for (int i = 0; i < n; i++) {
            dif[i] = emaFast[i] - emaSlow[i];
        }
        double[] dea = Indicators.ema(dif, signal);
        double[] hist = new double[n];
        for (int i = 0; i < n; i++) {
            hist[i] = 2 * (dif[i] - dea[i]);
        }
        return new Result(dif, dea, hist);
    }
}
