package com.stock.tracking;

import com.stock.alert.AlertService;
import com.stock.domain.SignalRecord;
import com.stock.domain.SignalTradeTrack;
import com.stock.domain.TradeTrackStatus;
import com.stock.indicator.IndicatorSeries;
import com.stock.indicator.IndicatorService;
import com.stock.repository.SignalRecordRepository;
import com.stock.repository.SignalTradeTrackRepository;
import com.stock.scan.RealtimeScanService;
import com.stock.signal.SignalType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 低9交易跟踪：创建、旧数据迁移、OPEN 状态重算、退出通知重试。
 */
@Slf4j
@Service
public class TradeTrackService {

    private final SignalTradeTrackRepository trackRepository;
    private final SignalRecordRepository signalRepository;
    private final IndicatorService indicatorService;
    private final RealtimeScanService realtimeScanService;
    private final TradeTrackEvaluator evaluator;
    private final AlertService alertService;

    public TradeTrackService(SignalTradeTrackRepository trackRepository,
                             SignalRecordRepository signalRepository,
                             IndicatorService indicatorService,
                             RealtimeScanService realtimeScanService,
                             TradeTrackEvaluator evaluator,
                             AlertService alertService) {
        this.trackRepository = trackRepository;
        this.signalRepository = signalRepository;
        this.indicatorService = indicatorService;
        this.realtimeScanService = realtimeScanService;
        this.evaluator = evaluator;
        this.alertService = alertService;
    }

    /** 为新产生的低9信号创建 OPEN 跟踪记录。 */
    public SignalTradeTrack createForNewSignal(SignalRecord signal) {
        if (signal == null || signal.getSignalType() != SignalType.BUY_LOW9) {
            return null;
        }
        return trackRepository.findBySignalId(signal.getId())
                .orElseGet(() -> trackRepository.save(fromSignal(signal)));
    }

    /**
     * 将历史低9信号同步到新表。历史上已经关闭的记录只回填状态，不补发旧通知；
     * 仍为 OPEN 的记录会从下一轮监控开始正常发送退出通知。
     */
    public SyncResult syncExistingSignals() {
        List<SignalRecord> signals =
                signalRepository.findBySignalTypeOrderByTradeDateAscIdAsc(SignalType.BUY_LOW9);
        Map<String, IndicatorSeries> seriesCache = new HashMap<>();
        int created = 0;
        int open = 0;
        int closed = 0;
        for (SignalRecord signal : signals) {
            if (trackRepository.existsBySignalId(signal.getId())) {
                continue;
            }
            SignalTradeTrack track = fromSignal(signal);
            IndicatorSeries series = seriesCache.computeIfAbsent(
                    signal.getCode(), indicatorService::compute);
            TradeTrackEvaluation evaluation = evaluator.evaluate(track, series);
            apply(track, evaluation);
            if (track.getStatus() == TradeTrackStatus.OPEN) {
                open++;
            } else {
                // 迁移前已经结束的历史计划不补发通知，避免启动时集中轰炸。
                track.setExitNotified(true);
                track.setNotifyChannel("MIGRATED");
                closed++;
            }
            trackRepository.save(track);
            created++;
        }
        SyncResult result = new SyncResult(created, open, closed);
        log.info("低9跟踪同步完成 created={} open={} closed={}", created, open, closed);
        return result;
    }

    /**
     * 使用数据库中已定稿的日K重新计算全部低9状态。
     * 用于修复错误实时行情造成的误判；不会使用当日临时行情，也不会补发历史退出通知。
     */
    public RebuildResult rebuildAllFromFinalizedKlines() {
        List<SignalRecord> signals =
                signalRepository.findBySignalTypeOrderByTradeDateAscIdAsc(SignalType.BUY_LOW9);
        Map<String, IndicatorSeries> seriesCache = new HashMap<>();
        int open = 0, tp = 0, sl = 0, time = 0;
        for (SignalRecord signal : signals) {
            SignalTradeTrack track = trackRepository.findBySignalId(signal.getId())
                    .orElseGet(() -> fromSignal(signal));
            reset(track, signal);
            IndicatorSeries series = seriesCache.computeIfAbsent(
                    signal.getCode(), indicatorService::compute);
            apply(track, evaluator.evaluate(track, series));
            if (track.getStatus() == TradeTrackStatus.OPEN) {
                track.setExitNotified(false);
                track.setNotifyChannel(null);
                open++;
            } else {
                // 这是状态修复，不应把历史结果当作新事件再次通知。
                track.setExitNotified(true);
                track.setNotifyChannel("REBUILT");
                if (track.getStatus() == TradeTrackStatus.TP) {
                    tp++;
                } else if (track.getStatus() == TradeTrackStatus.SL) {
                    sl++;
                } else {
                    time++;
                }
            }
            trackRepository.save(track);
        }
        RebuildResult result = new RebuildResult(signals.size(), open, tp, sl, time);
        log.info("低9跟踪状态重建完成 total={} open={} tp={} sl={} time={}",
                signals.size(), open, tp, sl, time);
        return result;
    }

    /**
     * 仅修复现有止损记录：用已定稿日K的收盘价跌破原止损价作为止损条件。
     * 未在收盘确认止损的记录继续判断后续止盈、到期或恢复为 OPEN。
     */
    public RebuildResult repairExistingStopLossesByClose() {
        List<SignalTradeTrack> stopLossTracks =
                trackRepository.findByStatusOrderBySignalDateAsc(TradeTrackStatus.SL);
        Map<String, IndicatorSeries> seriesCache = new HashMap<>();
        int open = 0, tp = 0, sl = 0, time = 0;
        for (SignalTradeTrack track : stopLossTracks) {
            SignalRecord signal = signalRepository.findById(track.getSignalId()).orElse(null);
            if (signal == null) {
                log.warn("跳过收盘止损修复：信号不存在 signalId={}", track.getSignalId());
                sl++;
                continue;
            }
            reset(track, signal);
            IndicatorSeries series = seriesCache.computeIfAbsent(
                    signal.getCode(), indicatorService::compute);
            apply(track, evaluator.evaluateWithCloseStop(track, series));
            if (track.getStatus() == TradeTrackStatus.OPEN) {
                track.setExitNotified(false);
                track.setNotifyChannel(null);
                open++;
            } else {
                track.setExitNotified(true);
                track.setNotifyChannel("REPAIRED_CLOSE");
                if (track.getStatus() == TradeTrackStatus.TP) {
                    tp++;
                } else if (track.getStatus() == TradeTrackStatus.SL) {
                    sl++;
                } else {
                    time++;
                }
            }
            trackRepository.save(track);
        }
        RebuildResult result = new RebuildResult(stopLossTracks.size(), open, tp, sl, time);
        log.info("历史止损按收盘价修复完成 total={} open={} tp={} sl={} time={}",
                stopLossTracks.size(), open, tp, sl, time);
        return result;
    }

    /** 盘中使用实时拼接K线检查 OPEN；收盘后可使用已落库日K。 */
    public int checkOpenTrades(boolean realtime) {
        List<SignalTradeTrack> openTracks =
                trackRepository.findByStatusOrderBySignalDateAsc(TradeTrackStatus.OPEN);
        Map<String, IndicatorSeries> seriesCache = new HashMap<>();
        int changed = 0;
        for (SignalTradeTrack track : openTracks) {
            try {
                IndicatorSeries series = seriesCache.computeIfAbsent(track.getCode(), code ->
                        realtime ? realtimeScanService.computeRealtimeSeries(code)
                                : indicatorService.compute(code));
                TradeTrackEvaluation evaluation = evaluator.evaluate(track, series, !realtime);
                apply(track, evaluation);
                if (realtime && track.getStatus() == TradeTrackStatus.OPEN
                        && series != null && !series.isEmpty()
                        && LocalDate.now().equals(series.last().getTradeDate())) {
                    TradeTrackEvaluation softStop = evaluator.evaluateSoftStop(
                            track, series.last(), LocalDateTime.now());
                    apply(track, softStop);
                }
                track = trackRepository.save(track);
                if (track.getStatus() != TradeTrackStatus.OPEN) {
                    changed++;
                    deliverExit(track);
                }
            } catch (Exception e) {
                log.error("检查低9交易计划失败 signalId={} code={} err={}",
                        track.getSignalId(), track.getCode(), e.getMessage());
            }
        }
        retryPendingNotifications();
        log.info("低9交易计划检查完成 open={} changed={} realtime={}",
                openTracks.size(), changed, realtime);
        return changed;
    }

    public SignalTradeTrack findBySignalId(Long signalId) {
        return trackRepository.findBySignalId(signalId).orElse(null);
    }

    private void retryPendingNotifications() {
        List<SignalTradeTrack> pending =
                trackRepository.findByExitNotifiedFalseAndStatusNotOrderByExitDateAsc(TradeTrackStatus.OPEN);
        for (SignalTradeTrack track : pending) {
            deliverExit(track);
        }
    }

    private void deliverExit(SignalTradeTrack track) {
        String delivered = alertService.dispatchExit(track, track.getNotifyChannel());
        track.setNotifyChannel(delivered);
        track.setExitNotified(alertService.exitDeliveryComplete(delivered));
        trackRepository.save(track);
    }

    private SignalTradeTrack fromSignal(SignalRecord signal) {
        SignalTradeTrack track = new SignalTradeTrack();
        track.setSignalId(signal.getId());
        track.setCode(signal.getCode());
        track.setName(signal.getName());
        track.setSignalDate(signal.getTradeDate());
        track.setEntryPrice(signal.getEntryPrice());
        track.setStopPrice(signal.getStopPrice());
        track.setTargetPrice(signal.getTargetPrice());
        track.setLatestExitDate(signal.getLatestExitDate());
        track.setStatus(TradeTrackStatus.OPEN);
        return track;
    }

    private void reset(SignalTradeTrack track, SignalRecord signal) {
        track.setCode(signal.getCode());
        track.setName(signal.getName());
        track.setSignalDate(signal.getTradeDate());
        track.setEntryPrice(signal.getEntryPrice());
        track.setStopPrice(signal.getStopPrice());
        track.setTargetPrice(signal.getTargetPrice());
        track.setLatestExitDate(signal.getLatestExitDate());
        track.setStatus(TradeTrackStatus.OPEN);
        track.setExitDate(null);
        track.setExitPrice(null);
        track.setReturnPct(null);
        track.setHoldDays(0);
        evaluator.clearSoftStopTimer(track);
        track.setExitNotified(false);
        track.setNotifyChannel(null);
    }

    private void apply(SignalTradeTrack track, TradeTrackEvaluation evaluation) {
        track.setEntryPrice(evaluation.getEntryPrice());
        track.setStopPrice(evaluation.getStopPrice());
        track.setTargetPrice(evaluation.getTargetPrice());
        track.setHoldDays(evaluation.getHoldDays());
        if (evaluation.getStatus() != TradeTrackStatus.OPEN) {
            track.setStatus(evaluation.getStatus());
            track.setExitDate(evaluation.getExitDate());
            track.setExitPrice(evaluation.getExitPrice());
            track.setReturnPct(evaluation.getReturnPct());
            evaluator.clearSoftStopTimer(track);
        }
    }

    public static class SyncResult {
        private final int created;
        private final int open;
        private final int closed;

        public SyncResult(int created, int open, int closed) {
            this.created = created;
            this.open = open;
            this.closed = closed;
        }

        public int getCreated() {
            return created;
        }

        public int getOpen() {
            return open;
        }

        public int getClosed() {
            return closed;
        }
    }

    public static class RebuildResult {
        private final int total;
        private final int open;
        private final int tp;
        private final int sl;
        private final int time;

        public RebuildResult(int total, int open, int tp, int sl, int time) {
            this.total = total;
            this.open = open;
            this.tp = tp;
            this.sl = sl;
            this.time = time;
        }

        public int getTotal() {
            return total;
        }

        public int getOpen() {
            return open;
        }

        public int getTp() {
            return tp;
        }

        public int getSl() {
            return sl;
        }

        public int getTime() {
            return time;
        }
    }
}
