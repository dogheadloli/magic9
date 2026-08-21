package com.stock.crypto;

/**
 * 加密货币监控周期（币安现货）。
 */
public enum CryptoInterval {
    D1("1d", "日K"),
    H4("4h", "4小时");

    private final String binance;
    private final String label;

    CryptoInterval(String binance, String label) {
        this.binance = binance;
        this.label = label;
    }

    public String getBinance() {
        return binance;
    }

    public String getLabel() {
        return label;
    }

    public static CryptoInterval fromParam(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return D1;
        }
        String v = raw.trim();
        for (CryptoInterval it : values()) {
            if (it.name().equalsIgnoreCase(v) || it.binance.equalsIgnoreCase(v)
                    || it.label.equals(v)) {
                return it;
            }
        }
        throw new IllegalArgumentException("不支持的周期: " + raw + "（可用 1d / 4h）");
    }
}
