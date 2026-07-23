package com.stock.tracking;

import com.stock.signal.SignalType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 信号事务提交后创建跟踪记录，避免跟踪表引用尚未提交的 signal_id。
 */
@Component
public class TradeTrackLifecycleListener {

    private final TradeTrackService tradeTrackService;

    public TradeTrackLifecycleListener(TradeTrackService tradeTrackService) {
        this.tradeTrackService = tradeTrackService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignalSaved(Low9SignalSavedEvent event) {
        if (event.getSignal().getSignalType() == SignalType.BUY_LOW9) {
            tradeTrackService.createForNewSignal(event.getSignal());
        }
    }
}
