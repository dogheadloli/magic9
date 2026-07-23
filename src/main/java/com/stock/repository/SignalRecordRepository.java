package com.stock.repository;

import com.stock.domain.SignalRecord;
import com.stock.signal.SignalType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SignalRecordRepository extends JpaRepository<SignalRecord, Long> {

    boolean existsByCodeAndTradeDateAndSignalType(String code, LocalDate tradeDate, SignalType signalType);

    Page<SignalRecord> findBySignalTypeOrderByTradeDateDescIdDesc(SignalType signalType, Pageable pageable);

    List<SignalRecord> findBySignalTypeOrderByTradeDateAscIdAsc(SignalType signalType);

    List<SignalRecord> findByTradeDateOrderByStrongDescScoreDesc(LocalDate tradeDate);

    List<SignalRecord> findByCodeOrderByTradeDateDesc(String code);

    List<SignalRecord> findByCodeAndSignalTypeAndTradeDateBetween(
            String code, SignalType signalType, LocalDate start, LocalDate end);

    List<SignalRecord> findTop200ByOrderByTradeDateDescIdDesc();
}
