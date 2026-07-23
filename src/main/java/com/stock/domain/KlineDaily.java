package com.stock.domain;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日K线（前复权）。
 */
@Data
@Entity
@Table(name = "kline_daily",
        uniqueConstraints = @UniqueConstraint(name = "uk_kline_code_date", columnNames = {"code", "trade_date"}))
public class KlineDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(precision = 18, scale = 3)
    private BigDecimal open;

    @Column(precision = 18, scale = 3)
    private BigDecimal high;

    @Column(precision = 18, scale = 3)
    private BigDecimal low;

    @Column(precision = 18, scale = 3)
    private BigDecimal close;

    /** 成交量（手） */
    private Long volume;

    /** 成交额（元） */
    @Column(precision = 20, scale = 2)
    private BigDecimal amount;

    /** 涨跌幅（%） */
    @Column(name = "change_pct", precision = 10, scale = 3)
    private BigDecimal changePct;
}
