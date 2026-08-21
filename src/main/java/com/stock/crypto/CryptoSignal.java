package com.stock.crypto;

import com.stock.signal.SignalType;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加密货币信号（按交易对 + 周期 + K线开盘时间去重）。
 */
@Data
@Entity
@Table(name = "crypto_signal",
        uniqueConstraints = @UniqueConstraint(name = "uk_crypto_signal_key",
                columnNames = {"symbol", "bar_interval", "open_time", "signal_type"}),
        indexes = {
                @Index(name = "idx_crypto_signal_sym", columnList = "symbol"),
                @Index(name = "idx_crypto_signal_time", columnList = "open_time")
        })
public class CryptoSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(length = 32)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "bar_interval", nullable = false, length = 8)
    private CryptoInterval interval;

    @Column(name = "open_time", nullable = false)
    private LocalDateTime openTime;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 16)
    private SignalType signalType;

    private int score;

    @Column(name = "max_score")
    private int maxScore;

    private boolean strong;

    @Column(name = "hit_factors", length = 256)
    private String hitFactors;

    @Lob
    @Column(name = "detail_json")
    private String detailJson;

    @Column(name = "entry_price")
    private Double entryPrice;

    @Column(name = "stop_price")
    private Double stopPrice;

    @Column(name = "target_price")
    private Double targetPrice;

    @Column(name = "latest_exit_date")
    private LocalDate latestExitDate;

    private boolean notified = false;

    @Column(name = "notify_channel", length = 32)
    private String notifyChannel;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void onCreate() {
        this.createTime = LocalDateTime.now();
    }
}
