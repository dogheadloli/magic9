package com.stock.domain;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 诊股结果缓存：同一只股票按交易日缓存一份，刷新可强制重算。
 */
@Data
@Entity
@Table(name = "stock_diagnosis",
        indexes = {@Index(name = "idx_diag_code_date", columnList = "code,trade_date")})
public class StockDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    private Integer score;

    @Column(length = 16)
    private String stance;

    private boolean aiUsed;

    /** 完整 DiagnosisView 的 JSON 序列化 */
    @Lob
    @Column(name = "payload")
    private String payload;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void onCreate() {
        this.createTime = LocalDateTime.now();
    }
}
