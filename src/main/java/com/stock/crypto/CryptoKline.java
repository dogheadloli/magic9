package com.stock.crypto;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 币安现货 K 线（日K / 4小时，原始价格，无复权）。
 */
@Data
@Entity
@Table(name = "crypto_kline",
        uniqueConstraints = @UniqueConstraint(name = "uk_crypto_kline_sym_int_time",
                columnNames = {"symbol", "bar_interval", "open_time"}),
        indexes = @Index(name = "idx_crypto_kline_sym_int", columnList = "symbol, bar_interval"))
public class CryptoKline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "bar_interval", nullable = false, length = 8)
    private CryptoInterval interval;

    @Column(name = "open_time", nullable = false)
    private LocalDateTime openTime;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(precision = 24, scale = 8)
    private BigDecimal open;

    @Column(precision = 24, scale = 8)
    private BigDecimal high;

    @Column(precision = 24, scale = 8)
    private BigDecimal low;

    @Column(precision = 24, scale = 8)
    private BigDecimal close;

    @Column(precision = 28, scale = 8)
    private BigDecimal volume;

    @Column(precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(name = "change_pct", precision = 12, scale = 4)
    private BigDecimal changePct;
}
