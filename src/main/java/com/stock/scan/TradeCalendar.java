package com.stock.scan;

import com.stock.config.ScheduleProperties;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * 交易日 / 交易时段判定。
 * <p>交易日：周一至周五且不在节假日列表中（节假日可在 schedule.holidays 配置）。
 * <p>交易时段：schedule.trade-sessions 配置的若干 "HH:mm-HH:mm" 区间。
 */
@Component
public class TradeCalendar {

    private final ScheduleProperties props;

    public TradeCalendar(ScheduleProperties props) {
        this.props = props;
    }

    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidaySet().contains(date.toString());
    }

    public boolean isInTradingSession(LocalTime time) {
        for (String s : props.getTradeSessions()) {
            String[] parts = s.split("-");
            if (parts.length != 2) {
                continue;
            }
            LocalTime start = LocalTime.parse(parts[0].trim(), DateTimeFormatter.ofPattern("H:mm"));
            LocalTime end = LocalTime.parse(parts[1].trim(), DateTimeFormatter.ofPattern("H:mm"));
            if (!time.isBefore(start) && !time.isAfter(end)) {
                return true;
            }
        }
        return false;
    }

    /** 当前是否处于可扫描状态（交易日 + 交易时段；force-scan 时恒为 true）。 */
    public boolean isScanAllowedNow() {
        if (props.isForceScan()) {
            return true;
        }
        LocalDate today = LocalDate.now();
        return isTradingDay(today) && isInTradingSession(LocalTime.now());
    }

    /**
     * 当天是否已经到达第一个交易时段的开始时间。
     * 开盘前及非交易日不得拼接实时K线；午休和收盘后仍可使用当日最后行情。
     */
    public boolean hasTradingStarted(LocalDate date, LocalTime time) {
        if (!isTradingDay(date)) {
            return false;
        }
        LocalTime firstStart = null;
        for (String session : props.getTradeSessions()) {
            String[] parts = session.split("-");
            if (parts.length != 2) {
                continue;
            }
            LocalTime start = LocalTime.parse(parts[0].trim(), DateTimeFormatter.ofPattern("H:mm"));
            if (firstStart == null || start.isBefore(firstStart)) {
                firstStart = start;
            }
        }
        return firstStart != null && !time.isBefore(firstStart);
    }

    public boolean hasTradingStartedToday() {
        return hasTradingStarted(LocalDate.now(), LocalTime.now());
    }

    /** 从 from 之后第 n 个交易日（不含 from 当日）。 */
    public LocalDate plusTradingDays(LocalDate from, int n) {
        LocalDate d = from;
        int added = 0;
        while (added < n) {
            d = d.plusDays(1);
            if (isTradingDay(d)) {
                added++;
            }
        }
        return d;
    }

    private Set<String> holidaySet() {
        return new HashSet<>(props.getHolidays());
    }
}
