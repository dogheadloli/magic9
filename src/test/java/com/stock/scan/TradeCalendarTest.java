package com.stock.scan;

import com.stock.config.ScheduleProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeCalendarTest {

    private final TradeCalendar calendar = calendar();

    @Test
    void rejectsRealtimeBarBeforeMarketOpen() {
        assertFalse(calendar.hasTradingStarted(
                LocalDate.of(2026, 7, 16), LocalTime.of(9, 29, 59)));
    }

    @Test
    void allowsRealtimeBarAfterMarketOpenIncludingLunch() {
        assertTrue(calendar.hasTradingStarted(
                LocalDate.of(2026, 7, 16), LocalTime.of(9, 30)));
        assertTrue(calendar.hasTradingStarted(
                LocalDate.of(2026, 7, 16), LocalTime.of(12, 0)));
    }

    private TradeCalendar calendar() {
        ScheduleProperties props = new ScheduleProperties();
        props.setTradeSessions(Arrays.asList("09:30-11:30", "13:00-15:00"));
        return new TradeCalendar(props);
    }
}
