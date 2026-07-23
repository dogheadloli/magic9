package com.stock.repository;

import com.stock.domain.KlineDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KlineDailyRepository extends JpaRepository<KlineDaily, Long> {

    List<KlineDaily> findByCodeOrderByTradeDateAsc(String code);

    Optional<KlineDaily> findByCodeAndTradeDate(String code, LocalDate tradeDate);

    Optional<KlineDaily> findTopByCodeOrderByTradeDateDesc(String code);

    void deleteByCode(String code);
}
