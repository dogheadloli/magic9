package com.stock.journal;

import lombok.Data;

import java.time.LocalDate;

/** 平仓请求。 */
@Data
public class CloseRequest {
    private LocalDate exitDate;
    private Double exitPrice;
}
