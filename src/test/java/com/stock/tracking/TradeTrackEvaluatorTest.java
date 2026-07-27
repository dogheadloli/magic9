package com.stock.tracking;

import com.stock.config.StrategyProperties;
import com.stock.domain.SignalTradeTrack;
import com.stock.domain.TradeTrackStatus;
import com.stock.indicator.BarIndicator;
import com.stock.indicator.IndicatorSeries;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    void hardStopWinsWhenSameBarAlsoTouchesTarget() {
        TradeTrackEvaluation result = evaluator.evaluate(track(), series(2, true, true));

        assertEquals(TradeTrackStatus.SL, result.getStatus());
        assertEquals(88.2, result.getExitPrice());
    }

    @Test
    void closeStopRepairIgnoresIntradayTouchRecoveredByClose() {
        IndicatorSeries series = series(2, false, false);
        series.getBars().get(1).setLow(89);
        series.getBars().get(1).setClose(91);

        TradeTrackEvaluation result = evaluator.evaluateWithCloseStop(track(), series);

        assertEquals(TradeTrackStatus.OPEN, result.getStatus());
        assertNull(result.getExitDate());
    }

    @Test
    void closeStopRepairExitsAtClosingPrice() {
        IndicatorSeries series = series(2, false, false);
        series.getBars().get(1).setLow(88);
        series.getBars().get(1).setClose(89);

        TradeTrackEvaluation result = evaluator.evaluateWithCloseStop(track(), series);

        assertEquals(TradeTrackStatus.SL, result.getStatus());
        assertEquals(89.0, result.getExitPrice());
    }

    @Test
    void softStopRequiresOneHourOfContinuousChecks() {
        SignalTradeTrack track = track();
        BarIndicator bar = realtimeBar(89);
        LocalDateTime start = LocalDateTime.of(2026, 1, 2, 10, 0);
        TradeTrackEvaluation result = null;

        for (int i = 0; i <= 12; i++) {
            result = evaluator.evaluateSoftStop(track, bar, start.plusMinutes(i * 5L));
        }

        assertEquals(TradeTrackStatus.SL, result.getStatus());
        assertEquals(89.0, result.getExitPrice());
        assertNull(track.getSoftStopStartedAt());
    }

    @Test
    void softStopTimerClearsAfterPriceRecovers() {
        SignalTradeTrack track = track();
        LocalDateTime start = LocalDateTime.of(2026, 1, 2, 10, 0);

        evaluator.evaluateSoftStop(track, realtimeBar(89), start);
        evaluator.evaluateSoftStop(track, realtimeBar(90), start.plusMinutes(5));
        TradeTrackEvaluation result =
                evaluator.evaluateSoftStop(track, realtimeBar(89), start.plusMinutes(10));

        assertEquals(TradeTrackStatus.OPEN, result.getStatus());
        assertEquals(start.plusMinutes(10), track.getSoftStopStartedAt());
    }

    @Test
    void softStopTimerRestartsAfterMonitoringGap() {
        SignalTradeTrack track = track();
        LocalDateTime start = LocalDateTime.of(2026, 1, 2, 10, 0);

        evaluator.evaluateSoftStop(track, realtimeBar(89), start);
        TradeTrackEvaluation result =
                evaluator.evaluateSoftStop(track, realtimeBar(89), start.plusMinutes(15));

        assertEquals(TradeTrackStatus.OPEN, result.getStatus());
        assertEquals(start.plusMinutes(15), track.getSoftStopStartedAt());
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
            bar.setLow(i == 1 && hitStop ? 87 : 99);
            bar.setHigh(i == 1 && hitTarget ? 121 : 101);
            bar.setMa20(120.0);
            bars.add(bar);
        }
        double[] empty = new double[count];
        return new IndicatorSeries("600000", bars, empty, empty, empty, empty, empty);
    }

    private BarIndicator realtimeBar(double close) {
        BarIndicator bar = new BarIndicator();
        bar.setTradeDate(LocalDate.of(2026, 1, 2));
        bar.setOpen(100);
        bar.setHigh(100);
        bar.setLow(close);
        bar.setClose(close);
        return bar;
    }
}
