package com.stock.repository;

import com.stock.domain.StockPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockPoolRepository extends JpaRepository<StockPool, Long> {

    Optional<StockPool> findByCode(String code);

    boolean existsByCode(String code);

    List<StockPool> findByEnabledTrue();
}
