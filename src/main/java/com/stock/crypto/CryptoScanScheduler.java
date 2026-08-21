package com.stock.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 币安现货 24/7 扫描（与 A 股交易时段无关）。
 */
@Slf4j
@Component
public class CryptoScanScheduler {

    private final CryptoProperties props;
    private final CryptoScanService scanService;

    public CryptoScanScheduler(CryptoProperties props, CryptoScanService scanService) {
        this.props = props;
        this.scanService = scanService;
    }

    @Scheduled(fixedRateString = "${crypto.scan.interval-ms:300000}")
    public void scan() {
        if (!props.isEnabled() || !props.getScan().isEnabled()) {
            return;
        }
        try {
            scanService.scanAll();
        } catch (Exception e) {
            log.error("加密定时扫描异常 err={}", e.getMessage());
        }
    }
}
