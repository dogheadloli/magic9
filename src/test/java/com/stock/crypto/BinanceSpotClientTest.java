package com.stock.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinanceSpotClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseKlines_readsUtcOpenTimeAndChangePct() throws Exception {
        String json = "["
                + "[1719792000000,\"60000\",\"61000\",\"59000\",\"60500\",\"12.5\",1719878399999,\"750000\",1,\"0\",\"0\",\"0\"],"
                + "[1719878400000,\"60500\",\"62000\",\"60000\",\"61600\",\"10.0\",1719964799999,\"616000\",1,\"0\",\"0\",\"0\"]"
                + "]";
        List<CryptoKlineBar> bars = BinanceSpotClient.parseKlines(mapper.readTree(json));
        assertEquals(2, bars.size());
        assertEquals(LocalDateTime.of(2024, 7, 1, 0, 0), bars.get(0).getOpenTime());
        assertEquals(LocalDate.of(2024, 7, 1), bars.get(0).getTradeDate());
        assertEquals(new BigDecimal("60500"), bars.get(0).getClose());
        assertEquals(new BigDecimal("12.5"), bars.get(0).getVolume());
        assertNotNull(bars.get(1).getChangePct());
        assertTrue(bars.get(1).getChangePct().doubleValue() > 1.8);
    }

    @Test
    void normalizeSymbol_stripsSeparators() {
        assertEquals("BTCUSDT", CryptoPoolService.normalizeSymbol("btc-usdt"));
        assertEquals("ETHUSDT", CryptoPoolService.normalizeSymbol("eth/usdt"));
    }

    @Test
    void intervalFromParam_acceptsAliases() {
        assertEquals(CryptoInterval.H4, CryptoInterval.fromParam("4h"));
        assertEquals(CryptoInterval.D1, CryptoInterval.fromParam("1d"));
        assertEquals(CryptoInterval.H4, CryptoInterval.fromParam("4小时"));
    }
}
