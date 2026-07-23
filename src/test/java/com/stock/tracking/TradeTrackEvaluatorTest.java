package com.stock.tracking;

import com.stock.config.StrategyProperties;
import com.stock.domain.SignalTradeTrack;
import com.stock.domain.TradeTrackStatus;
import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TradeTrackEvaluatorTest {

    private final TradeTrackEvaluator evaluator = new TradeTrackEvaluator(new StrategyProperties());

    @Test
    void remainsOpenBeforeMaximumHoldingBars() {
        TradeTrackEvaluation result = evaluator.evaluate(track(), series(5, false, false));

        assertEquals(TradeTrackStatus.OPEN, result.getStatus());
        assertEquals(4, result.getHoldDays());
        assertNull(result.getExitDate());
    }

    @Test
    void expiresOnlyAfterFullHoldingBars() {
        TradeTrackEvaluation result = evaluator.evaluate(track(), series(16, false, false));

        assertEquals(TradeTrackStatus.TIME, result.getStatus());
        assertEquals(15, result.getHoldDays());
        assertEquals(LocalDate.of(2026, 1, 16), result.getExitDate());
    }

    @Test
    void doesNotExpireDuringIntradayCheck() {
        TradeTrackEvaluation result = evaluator.evaluate(track(), series(16, false, false), false);

        assertEquals(TradeTrackStatus.OPEN, result.getStatus());
        assertNull(result.getExitDate());
    }

    @Test
    void stopLossWinsWhenSameBarAlsoTouchesTarget() {
        TradeTrackEvaluation result = evaluator.evaluate(track(), series(2, true, true));

        assertEquals(TradeTrackStatus.SL, result.getStatus());
        assertEquals(90.0, result.getExitPrice());
    }

    private SignalTradeTrack track() {
        SignalTradeTrack track = new SignalTradeTrack();
        track.setSignalId(1L);
        track.setCode("600000");
        track.setSignalDate(LocalDate.of(2026, 1, 1));
        track.setEntryPrice(100.0);
        track.setStopPrice(90.0);
        track.setTargetPrice(120.0);
        return track;
    }

    private IndicatorSeries series(int count, boolean hitStop, boolean hitTarget) {
        List<BarIndicator> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BarIndicator bar = new BarIndicator();
            bar.setTradeDate(LocalDate.of(2026, 1, 1).plusDays(i));
            bar.setOpen(100);
            bar.setClose(100);
            bar.setLow(i == 1 && hitStop ? 89 : 99);
            bar.setHigh(i == 1 && hitTarget ? 121 : 101);
            bar.setMa20(120.0);
            bars.add(bar);
        }
        double[] empty = new double[count];
        return new IndicatorSeries("600000", bars, empty, empty, empty, empty, empty);
    }
}
