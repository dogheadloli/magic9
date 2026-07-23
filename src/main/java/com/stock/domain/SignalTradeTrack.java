package com.stock.domain;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 低9信号的持仓跟踪状态。
 *
 * SignalRecord 保存不可变的信号事实，本表保存随行情变化的 OPEN/TP/SL/TIME 生命周期。
 */
@Data
@Entity
@Table(name = "signal_trade_track",
        uniqueConstraints = @UniqueConstraint(name = "uk_trade_track_signal", columnNames = "signal_id"))
public class SignalTradeTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false)
    private Long signalId;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(length = 32)
    private String name;

    @Column(name = "signal_date", nullable = false)
    private LocalDate signalDate;

    @Column(name = "entry_price")
    private Double entryPrice;

    @Column(name = "stop_price")
    private Double stopPrice;

    /** 信号产生时的目标价快照；实际止盈仍按每日动态 MA20 判断。 */
    @Column(name = "target_price")
    private Double targetPrice;

    @Column(name = "latest_exit_date")
    private LocalDate latestExitDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TradeTrackStatus status = TradeTrackStatus.OPEN;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "exit_price")
    private Double exitPrice;

    @Column(name = "return_pct")
    private Double returnPct;

    @Column(name = "hold_days")
    private Integer holdDays;

    /** 退出事件是否已成功投递至少一个渠道。 */
    @Column(name = "exit_notified")
    private boolean exitNotified;

    @Column(name = "notify_channel", length = 64)
    private String notifyChannel;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Version
    private Long version;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
    }

    @PreUpdate
    public void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
