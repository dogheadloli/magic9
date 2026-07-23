package com.stock.repository;

import com.stock.domain.ManualTrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManualTradeRepository extends JpaRepository<ManualTrade, Long> {

    List<ManualTrade> findByOrderByStatusAscEntryDateDesc();

    List<ManualTrade> findByCodeOrderByEntryDateDesc(String code);
}
