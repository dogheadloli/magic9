package com.stock.web;

import com.stock.analysis.AnalysisService;
import com.stock.analysis.CurrentDecision;
import com.stock.analysis.HistoryReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买卖分析接口：当下决策 + 历史逐笔复盘。
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/{code}/current")
    public CurrentDecision current(@PathVariable String code) {
        return analysisService.current(code);
    }

    @GetMapping("/{code}/history")
    public HistoryReport history(@PathVariable String code) {
        return analysisService.history(code);
    }
}
