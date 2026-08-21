package com.stock.crypto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CryptoKlineRepository extends JpaRepository<CryptoKline, Long> {

    List<CryptoKline> findBySymbolAndIntervalOrderByOpenTimeAsc(String symbol, CryptoInterval interval);

    Optional<CryptoKline> findBySymbolAndIntervalAndOpenTime(String symbol, CryptoInterval interval,
                                                            LocalDateTime openTime);

    long countBySymbolAndInterval(String symbol, CryptoInterval interval);
}
