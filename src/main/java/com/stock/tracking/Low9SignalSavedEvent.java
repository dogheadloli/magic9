package com.stock.tracking;

import com.stock.domain.SignalRecord;

/**
 * 低9信号成功落库事件。
 */
public class Low9SignalSavedEvent {
    private final SignalRecord signal;

    public Low9SignalSavedEvent(SignalRecord signal) {
        this.signal = signal;
    }

    public SignalRecord getSignal() {
        return signal;
    }
}
