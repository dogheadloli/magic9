package com.stock.crypto;

import com.stock.signal.SignalType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CryptoSignalRepository extends JpaRepository<CryptoSignal, Long> {

    boolean existsBySymbolAndIntervalAndOpenTimeAndSignalType(
            String symbol, CryptoInterval interval, LocalDateTime openTime, SignalType signalType);

    List<CryptoSignal> findTop200ByOrderByOpenTimeDescIdDesc();

    List<CryptoSignal> findBySymbolOrderByOpenTimeDesc(String symbol);

    List<CryptoSignal> findBySymbolAndIntervalAndSignalTypeAndOpenTimeBetween(
            String symbol, CryptoInterval interval, SignalType signalType,
            LocalDateTime start, LocalDateTime end);
}
