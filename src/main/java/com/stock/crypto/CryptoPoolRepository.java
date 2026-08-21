package com.stock.crypto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CryptoPoolRepository extends JpaRepository<CryptoPool, Long> {

    Optional<CryptoPool> findBySymbol(String symbol);

    List<CryptoPool> findByEnabledTrue();
}
