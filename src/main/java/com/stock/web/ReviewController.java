package com.stock.web;

import com.stock.analysis.ReviewPage;
import com.stock.analysis.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 低9信号历史复盘接口。
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 分页拉取数据库中的低9信号并复盘（默认每页20条）。 */
    @GetMapping("/low9")
    public ReviewPage low9(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return reviewService.low9(page, size);
    }
}
