package com.stock.repository;

import com.stock.domain.StockDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface StockDiagnosisRepository extends JpaRepository<StockDiagnosis, Long> {

    Optional<StockDiagnosis> findFirstByCodeAndTradeDateOrderByIdDesc(String code, LocalDate tradeDate);
}
