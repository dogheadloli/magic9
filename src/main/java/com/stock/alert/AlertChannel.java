package com.stock.alert;

import com.stock.domain.SignalTradeTrack;
import com.stock.signal.SignalResult;

/**
 * 预警通知渠道（可插拔）。
 */
public interface AlertChannel {

    /** 渠道标识，与 alert.channels 配置匹配（如 WECOM / CONSOLE）。 */
    String name();

    /** 渠道是否就绪（如企业微信需配置 webhook key）。 */
    boolean isReady();

    /** 发送一条信号预警。 */
    void send(SignalResult signal);

    /** 发送一条低9交易计划退出通知。 */
    void sendExit(SignalTradeTrack track);
}
