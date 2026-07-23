package com.stock.tracking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动后自动将现有低9信号补入跟踪表。
 */
@Slf4j
@Component
public class TradeTrackInitializer {

    private final TradeTrackService tradeTrackService;

    public TradeTrackInitializer(TradeTrackService tradeTrackService) {
        this.tradeTrackService = tradeTrackService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            tradeTrackService.syncExistingSignals();
        } catch (Exception e) {
            log.error("低9跟踪历史同步失败 err={}", e.getMessage());
        }
    }
}
