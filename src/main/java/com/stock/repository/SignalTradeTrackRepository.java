package com.stock.repository;

import com.stock.domain.SignalTradeTrack;
import com.stock.domain.TradeTrackStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignalTradeTrackRepository extends JpaRepository<SignalTradeTrack, Long> {

    Optional<SignalTradeTrack> findBySignalId(Long signalId);

    boolean existsBySignalId(Long signalId);

    List<SignalTradeTrack> findBySignalIdIn(List<Long> signalIds);

    List<SignalTradeTrack> findByStatusOrderBySignalDateAsc(TradeTrackStatus status);

    List<SignalTradeTrack> findByExitNotifiedFalseAndStatusNotOrderByExitDateAsc(TradeTrackStatus status);

    long countByStatusNot(TradeTrackStatus status);

    long countByStatusNotAndReturnPctGreaterThan(TradeTrackStatus status, Double returnPct);
}
