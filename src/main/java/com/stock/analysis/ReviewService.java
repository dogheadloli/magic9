package com.stock.analysis;

import com.stock.domain.SignalRecord;
import com.stock.domain.SignalTradeTrack;
import com.stock.domain.TradeTrackStatus;
import com.stock.repository.SignalRecordRepository;
import com.stock.repository.SignalTradeTrackRepository;
import com.stock.signal.SignalFactor;
import com.stock.signal.SignalType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 低9信号历史复盘：从数据库分页取出所有低9信号，逐条用回测优选规则模拟其结果。
 */
@Service
public class ReviewService {

    private final SignalRecordRepository signalRepository;
    private final SignalTradeTrackRepository trackRepository;

    public ReviewService(SignalRecordRepository signalRepository,
                         SignalTradeTrackRepository trackRepository) {
        this.signalRepository = signalRepository;
        this.trackRepository = trackRepository;
    }

    public ReviewPage low9(int page, int size) {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0 || size > 100) {
            size = 20;
        }
        Page<SignalRecord> pg = signalRepository.findBySignalTypeOrderByTradeDateDescIdDesc(
                SignalType.BUY_LOW9, PageRequest.of(page, size));

        ReviewPage rp = new ReviewPage();
        rp.setPage(page);
        rp.setSize(size);
        rp.setTotalElements(pg.getTotalElements());
        rp.setTotalPages(pg.getTotalPages());

        List<Long> signalIds = pg.getContent().stream()
                .map(SignalRecord::getId).collect(Collectors.toList());
        Map<Long, SignalTradeTrack> tracks = new HashMap<>();
        for (SignalTradeTrack track : signalIds.isEmpty()
                ? Collections.<SignalTradeTrack>emptyList()
                : trackRepository.findBySignalIdIn(signalIds)) {
            tracks.put(track.getSignalId(), track);
        }
        int closed = 0, wins = 0;
        for (SignalRecord rec : pg.getContent()) {
            ReviewItem it = new ReviewItem();
            it.setId(rec.getId());
            it.setCode(rec.getCode());
            it.setName(rec.getName());
            it.setTradeDate(rec.getTradeDate());
            it.setScore(rec.getScore());
            it.setMaxScore(rec.getMaxScore());
            it.setStrong(rec.isStrong());
            it.setFactors(labels(rec.getHitFactors()));
            it.setLatestExitDate(rec.getLatestExitDate());
            it.setStopPrice(rec.getStopPrice());
            it.setTargetPrice(rec.getTargetPrice());

            SignalTradeTrack track = tracks.get(rec.getId());
            if (track == null) {
                it.setReason("NA");
                it.setEntryPrice(rec.getEntryPrice());
            } else {
                it.setEntryPrice(track.getEntryPrice());
                it.setStopPrice(track.getStopPrice());
                it.setTargetPrice(track.getTargetPrice());
                it.setLatestExitDate(track.getLatestExitDate());
                it.setExitDate(track.getExitDate());
                it.setExitPrice(track.getExitPrice());
                it.setReason(track.getStatus().name());
                it.setReturnPct(track.getReturnPct());
                it.setHoldDays(track.getHoldDays());
                if (track.getStatus() != TradeTrackStatus.OPEN) {
                    closed++;
                    if (track.getReturnPct() != null && track.getReturnPct() > 0) {
                        wins++;
                    }
                }
            }
            rp.getItems().add(it);
        }
        rp.setPageClosed(closed);
        rp.setPageWins(wins);
        rp.setTotalClosed(trackRepository.countByStatusNot(TradeTrackStatus.OPEN));
        rp.setTotalWins(trackRepository.countByStatusNotAndReturnPctGreaterThan(
                TradeTrackStatus.OPEN, 0d));
        return rp;
    }

    private List<String> labels(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) {
            return out;
        }
        for (String n : csv.split(",")) {
            String t = n.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(SignalFactor.valueOf(t).getLabel());
            } catch (IllegalArgumentException e) {
                out.add(t);
            }
        }
        return out;
    }
}
