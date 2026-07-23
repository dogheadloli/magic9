package com.stock.scan;

import com.stock.datafetch.StockQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeScanServiceTest {

    @Test
    void rejectsPreOpenZeroQuote() {
        StockQuote quote = quote("0", "0", "0", "0");

        assertFalse(RealtimeScanService.isUsableQuote(quote));
    }

    @Test
    void acceptsValidTradingQuote() {
        StockQuote quote = quote("10.20", "10.00", "10.30", "9.95");

        assertTrue(RealtimeScanService.isUsableQuote(quote));
    }

    @Test
    void rejectsInconsistentOhlc() {
        StockQuote quote = quote("10.50", "10.00", "10.30", "9.95");

        assertFalse(RealtimeScanService.isUsableQuote(quote));
    }

    private StockQuote quote(String price, String open, String high, String low) {
        StockQuote q = new StockQuote();
        q.setPrice(new BigDecimal(price));
        q.setOpen(new BigDecimal(open));
        q.setHigh(new BigDecimal(high));
        q.setLow(new BigDecimal(low));
        return q;
    }
}
