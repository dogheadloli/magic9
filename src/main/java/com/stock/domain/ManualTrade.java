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
import javax.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 实盘记账：手动录入的一笔多头交易（持仓中 / 已平仓）。
 */
@Data
@Entity
@Table(name = "manual_trade")
public class ManualTrade {

    public enum Status { OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(length = 32)
    private String name;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "entry_price", nullable = false)
    private Double entryPrice;

    /** 股数 */
    @Column(nullable = false)
    private Integer qty;

    @Column(name = "stop_price")
    private Double stopPrice;

    @Column(name = "target_price")
    private Double targetPrice;

    @Column(name = "latest_exit_date")
    private LocalDate latestExitDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "exit_price")
    private Double exitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status = Status.OPEN;

    @Column(length = 128)
    private String note;

    /** 关联的信号记录id（按信号建仓时回填） */
    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void onCreate() {
        this.createTime = LocalDateTime.now();
    }
}
