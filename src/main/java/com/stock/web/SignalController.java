package com.stock.web;

import com.stock.domain.SignalRecord;
import com.stock.scan.RealtimeScanService;
import com.stock.signal.SignalResult;
import com.stock.signal.SignalService;
import com.stock.signal.SignalType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 信号评估与扫描接口。
 */
@RestController
public class SignalController {

    private final SignalService signalService;
    private final RealtimeScanService realtimeScanService;

    public SignalController(SignalService signalService, RealtimeScanService realtimeScanService) {
        this.signalService = signalService;
        this.realtimeScanService = realtimeScanService;
    }

    /** 评估单只股票信号（不落库）；asOf 可选，截至该交易日做历史评估。 */
    @GetMapping("/api/signal/evaluate")
    public List<SignalResult> evaluate(
            @RequestParam String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        Optional<SignalResult> r = signalService.evaluate(code, asOf);
        return r.map(Collections::singletonList).orElse(Collections.emptyList());
    }

    /** 扫描全部启用的自选股（基于已落库日K），命中落库并返回。 */
    @PostMapping("/api/signal/scan")
    public List<SignalResult> scan() {
        return signalService.scanEnabled();
    }

    /** 盘中实时扫描（拼接当日快照），命中落库+推送并返回。 */
    @PostMapping("/api/signal/scan-realtime")
    public List<SignalResult> scanRealtime() {
        return realtimeScanService.scan();
    }

    /** 信号记录查询（可按 code/date/type 过滤）。 */
    @GetMapping("/api/signals")
    public List<SignalRecord> signals(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) SignalType type) {
        return signalService.query(code, date, type);
    }
}
