package com.stock.journal;

import lombok.Data;

import java.time.LocalDate;

/** 建仓请求。 */
@Data
public class JournalRequest {
    private String code;
    private String name;
    private LocalDate entryDate;
    private Double entryPrice;
    private Integer qty;
    private Double stopPrice;
    private Double targetPrice;
    private LocalDate latestExitDate;
    private String note;
    private Long signalId;
}
