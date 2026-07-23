package com.stock.tracking;

import com.stock.domain.TradeTrackStatus;
import lombok.Data;

import java.time.LocalDate;

/**
 * 一次交易跟踪状态计算的纯结果。
 */
@Data
public class TradeTrackEvaluation {
    private TradeTrackStatus status = TradeTrackStatus.OPEN;
    private Double entryPrice;
    private Double stopPrice;
    private Double targetPrice;
    private LocalDate exitDate;
    private Double exitPrice;
    private Double returnPct;
    private int holdDays;
}
