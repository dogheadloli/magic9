package com.stock.alert;

import com.stock.config.AlertProperties;
import com.stock.domain.SignalTradeTrack;
import com.stock.signal.SignalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预警分发：按 alert.channels 配置将信号发送到各就绪渠道。
 */
@Slf4j
@Service
public class AlertService {

    private final AlertProperties props;
    private final List<AlertChannel> channels;

    public AlertService(AlertProperties props, List<AlertChannel> channels) {
        this.props = props;
        this.channels = channels;
    }

    /**
     * 分发一条信号，返回实际成功投递的渠道名（逗号分隔）。
     */
    public String dispatch(SignalResult signal) {
        List<String> enabled = enabledNames();
        StringBuilder used = new StringBuilder();
        for (AlertChannel ch : channels) {
            if (!enabled.contains(ch.name())) {
                continue;
            }
            if (!ch.isReady()) {
                log.warn("渠道 {} 未就绪，跳过", ch.name());
                continue;
            }
            try {
                ch.send(signal);
                if (used.length() > 0) {
                    used.append(",");
                }
                used.append(ch.name());
            } catch (Exception e) {
                log.error("渠道 {} 推送异常 err={}", ch.name(), e.getMessage());
            }
        }
        return used.toString();
    }

    /**
     * 分发交易退出通知。alreadyDelivered 中的渠道不会重复发送，返回累计成功渠道。
     */
    public String dispatchExit(SignalTradeTrack track, String alreadyDelivered) {
        Set<String> delivered = csvSet(alreadyDelivered);
        List<String> enabled = enabledNames();
        for (AlertChannel ch : channels) {
            if (!enabled.contains(ch.name()) || delivered.contains(ch.name()) || !ch.isReady()) {
                continue;
            }
            try {
                ch.sendExit(track);
                delivered.add(ch.name());
            } catch (Exception e) {
                log.error("渠道 {} 退出通知异常 signalId={} err={}",
                        ch.name(), track.getSignalId(), e.getMessage());
            }
        }
        return String.join(",", delivered);
    }

    /** 当前启用且就绪的渠道是否都已投递。 */
    public boolean exitDeliveryComplete(String deliveredCsv) {
        Set<String> delivered = csvSet(deliveredCsv);
        for (AlertChannel ch : channels) {
            if (enabledNames().contains(ch.name()) && ch.isReady() && !delivered.contains(ch.name())) {
                return false;
            }
        }
        return true;
    }

    private List<String> enabledNames() {
        return props.getChannels().stream()
                .map(String::toUpperCase).collect(Collectors.toList());
    }

    private Set<String> csvSet(String csv) {
        Set<String> result = new LinkedHashSet<>();
        if (csv == null || csv.trim().isEmpty()) {
            return result;
        }
        for (String value : csv.split(",")) {
            if (!value.trim().isEmpty()) {
                result.add(value.trim().toUpperCase());
            }
        }
        return result;
    }
}
