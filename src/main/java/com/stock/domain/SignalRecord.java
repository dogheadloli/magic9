package com.stock.domain;

import com.stock.signal.SignalType;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 信号记录（同一标的同向同日去重）。
 */
@Data
@Entity
@Table(name = "signal_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_signal_code_date_type",
                columnNames = {"code", "trade_date", "signal_type"}))
public class SignalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(length = 32)
    private String name;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 16)
    private SignalType signalType;

    private int score;

    @Column(name = "max_score")
    private int maxScore;

    private boolean strong;

    /** 命中要素（枚举名逗号分隔） */
    @Column(name = "hit_factors", length = 256)
    private String hitFactors;

    @Lob
    @Column(name = "detail_json")
    private String detailJson;

    /** 交易计划（仅低9买入信号）。 */
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
