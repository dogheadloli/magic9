package com.stock.alert;

import com.stock.domain.SignalTradeTrack;
import com.stock.signal.SignalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 控制台/日志渠道（始终可用）。
 */
@Slf4j
@Component
public class ConsoleAlertChannel implements AlertChannel {

    @Override
    public String name() {
        return "CONSOLE";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void send(SignalResult signal) {
        log.info(AlertMessageFormatter.toPlainText(signal));
    }

    @Override
    public void sendExit(SignalTradeTrack track) {
        log.info(AlertMessageFormatter.exitToPlainText(track));
    }
}
