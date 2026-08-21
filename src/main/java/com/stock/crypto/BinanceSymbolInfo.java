package com.stock.crypto;

import lombok.Data;

@Data
public class BinanceSymbolInfo {
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private String status;
}
