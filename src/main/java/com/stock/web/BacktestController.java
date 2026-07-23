package com.stock.web;

import com.stock.backtest.BacktestReport;
import com.stock.backtest.BacktestService;
import com.stock.backtest.ExitStrategyComparison;
import com.stock.backtest.ExitStrategyComparisonService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回测接口：对自选股历史跑「低9」策略，比较各止盈止损组合绩效。
 */
@RestController
public class BacktestController {

    private final BacktestService backtestService;
    private final ExitStrategyComparisonService exitStrategyComparisonService;

    public BacktestController(BacktestService backtestService,
                              ExitStrategyComparisonService exitStrategyComparisonService) {
        this.backtestService = backtestService;
        this.exitStrategyComparisonService = exitStrategyComparisonService;
    }

    @PostMapping("/api/backtest")
    public BacktestReport run() {
        return backtestService.run();
    }

    @GetMapping("/api/backtest")
    public BacktestReport runGet() {
        return backtestService.run();
    }

    /** 数据库全部低9信号的退出策略对比。 */
    @GetMapping("/api/backtest/exit-comparison")
    public ExitStrategyComparison compareExitStrategies() {
        return exitStrategyComparisonService.run();
    }
}
