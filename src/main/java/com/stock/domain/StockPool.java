package com.stock.domain;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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
 * 自选股池。
 */
@Data
@Entity
@Table(name = "stock_pool",
        uniqueConstraints = @UniqueConstraint(name = "uk_pool_code", columnNames = "code"),
        indexes = @Index(name = "idx_pool_group", columnList = "group_name"))
public class StockPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 6 位股票代码 */
    @Column(nullable = false, length = 16)
    private String code;

    /** 股票名称 */
    @Column(length = 32)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 4)
    private Market market;

    /** 分组（自选/行业/策略等） */
    @Column(name = "group_name", length = 32)
    private String groupName;

    /** 是否启用监控 */
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
