package com.stock.indicator;

import java.util.ArrayList;
import java.util.List;

/**
 * MACD 背离检测。
 * <p>底背离：价格创新低（后一个低点更低），而 DIF 不创新低（后一个低点更高）。
 * <p>顶背离：价格创新高（后一个高点更高），而 DIF 不创新高（后一个高点更低）。
 * <p>仅比较窗口内"已确认"的两个最近摆动极值（极值点右侧需有 swingK 根确认）。
 */
public final class DivergenceDetector {

    private DivergenceDetector() {
    }

    /** 底背离（基于最低价 low 与 DIF）。 */
    public static boolean bullish(double[] low, double[] dif, int window, int swingK) {
        List<Integer> lows = swingLows(low, swingK, window);
        if (lows.size() < 2) {
            return false;
        }
        int p1 = lows.get(lows.size() - 2);
        int p2 = lows.get(lows.size() - 1);
        return low[p2] < low[p1] && dif[p2] > dif[p1];
    }

    /** 顶背离（基于最高价 high 与 DIF）。 */
    public static boolean bearish(double[] high, double[] dif, int window, int swingK) {
        List<Integer> highs = swingHighs(high, swingK, window);
        if (highs.size() < 2) {
            return false;
        }
        int p1 = highs.get(highs.size() - 2);
        int p2 = highs.get(highs.size() - 1);
        return high[p2] > high[p1] && dif[p2] < dif[p1];
    }

    private static List<Integer> swingLows(double[] v, int k, int window) {
        int n = v.length;
        int start = Math.max(k, n - window);
        List<Integer> result = new ArrayList<>();
        for (int i = start; i < n - k; i++) {
            if (isLocalMin(v, i, k)) {
                result.add(i);
            }
        }
        return result;
    }

    private static List<Integer> swingHighs(double[] v, int k, int window) {
        int n = v.length;
        int start = Math.max(k, n - window);
        List<Integer> result = new ArrayList<>();
        for (int i = start; i < n - k; i++) {
            if (isLocalMax(v, i, k)) {
                result.add(i);
            }
        }
        return result;
    }

    private static boolean isLocalMin(double[] v, int i, int k) {
        for (int j = i - k; j <= i + k; j++) {
            if (j != i && v[j] < v[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLocalMax(double[] v, int i, int k) {
        for (int j = i - k; j <= i + k; j++) {
            if (j != i && v[j] > v[i]) {
                return false;
            }
        }
        return true;
    }
}
