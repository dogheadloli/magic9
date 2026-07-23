package com.stock.web;

import com.stock.diagnosis.DiagnosisService;
import com.stock.diagnosis.DiagnosisView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 诊股接口。
 */
@RestController
@RequestMapping("/api/diagnosis")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    /** 诊断该股票；默认走当日缓存，refresh=true 强制重算。 */
    @GetMapping("/{code}")
    public DiagnosisView diagnose(@PathVariable String code,
                                  @RequestParam(defaultValue = "false") boolean refresh) {
        return diagnosisService.diagnose(code, refresh);
    }
}
