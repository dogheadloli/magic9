package com.stock.journal;

import com.stock.domain.ManualTrade;
import lombok.Data;

import java.time.LocalDate;

/** 持仓/交易展示行（含现价与盈亏）。 */
@Data
public class TradeView {
    private Long id;
    private String code;
    private String name;
    private LocalDate entryDate;
    private Double entryPrice;
    private Integer qty;
    private Double stopPrice;
    private Double targetPrice;
    private LocalDate latestExitDate;
    private LocalDate exitDate;
    private Double exitPrice;
    private String status;
    private String note;

    /** 持仓中=现价；已平仓=出场价 */
    private Double price;
    private Double pnl;
    private Double returnPct;

    public static TradeView of(ManualTrade t) {
        TradeView v = new TradeView();
        v.id = t.getId();
        v.code = t.getCode();
        v.name = t.getName();
        v.entryDate = t.getEntryDate();
        v.entryPrice = t.getEntryPrice();
        v.qty = t.getQty();
        v.stopPrice = t.getStopPrice();
        v.targetPrice = t.getTargetPrice();
        v.latestExitDate = t.getLatestExitDate();
        v.exitDate = t.getExitDate();
        v.exitPrice = t.getExitPrice();
        v.status = t.getStatus().name();
        v.note = t.getNote();
        return v;
    }
}
