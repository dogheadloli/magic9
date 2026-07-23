package com.stock.indicator;

/**
 * 神奇九转（TD 序列）计数。
 * <p>低9：连续 setupLength 根 收盘价 &lt; 前4根收盘价；
 * 高9：连续 setupLength 根 收盘价 &gt; 前4根收盘价。
 */
public final class Td9Calculator {

    private Td9Calculator() {
    }

    public static class Result {
        public final int[] buySetup;   // 低9计数
        public final int[] sellSetup;  // 高9计数
        public final TdSignal[] signal;

        Result(int[] buySetup, int[] sellSetup, TdSignal[] signal) {
            this.buySetup = buySetup;
            this.sellSetup = sellSetup;
            this.signal = signal;
        }
    }

    public static Result compute(double[] close, int setupLength) {
        int n = close.length;
        int[] buy = new int[n];
        int[] sell = new int[n];
        TdSignal[] sig = new TdSignal[n];
        for (int i = 0; i < n; i++) {
            sig[i] = TdSignal.NONE;
            if (i < 4) {
                continue;
            }
            if (close[i] < close[i - 4]) {
                buy[i] = buy[i - 1] + 1;
                sell[i] = 0;
            } else if (close[i] > close[i - 4]) {
                sell[i] = sell[i - 1] + 1;
                buy[i] = 0;
            } else {
                buy[i] = 0;
                sell[i] = 0;
            }
            if (buy[i] == setupLength) {
                sig[i] = TdSignal.LOW_9;
            } else if (sell[i] == setupLength) {
                sig[i] = TdSignal.HIGH_9;
            }
        }
        return new Result(buy, sell, sig);
    }
}
