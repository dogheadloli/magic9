package com.stock.crypto;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 币安现货监控池（与股票自选池隔离）。
 */
@Data
@Entity
@Table(name = "crypto_pool",
        uniqueConstraints = @UniqueConstraint(name = "uk_crypto_pool_symbol", columnNames = "symbol"),
        indexes = @Index(name = "idx_crypto_pool_group", columnList = "group_name"))
public class CryptoPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(length = 32)
    private String name;

    @Column(name = "quote_asset", length = 16)
    private String quoteAsset;

    @Column(name = "group_name", length = 32)
    private String groupName;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;
        if (this.enabled == null) {
            this.enabled = true;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
