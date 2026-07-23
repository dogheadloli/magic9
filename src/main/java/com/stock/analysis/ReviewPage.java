package com.stock.analysis;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 低9信号复盘分页结果。
 */
@Data
public class ReviewPage {
    private List<ReviewItem> items = new ArrayList<>();
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    /** 本页已了结笔数 / 其中盈利笔数（用于展示本页胜负） */
    private int pageClosed;
    private int pageWins;
}
