package com.stock.alert;

import com.stock.config.AlertProperties;
import com.stock.domain.SignalTradeTrack;
import com.stock.signal.SignalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信群机器人渠道（markdown 消息）。
 */
@Slf4j
@Component
public class WeComAlertChannel implements AlertChannel {

    private final AlertProperties props;
    private final RestTemplate restTemplate;

    public WeComAlertChannel(AlertProperties props, RestTemplate marketRestTemplate) {
        this.props = props;
        this.restTemplate = marketRestTemplate;
    }

    @Override
    public String name() {
        return "WECOM";
    }

    @Override
    public boolean isReady() {
        return StringUtils.hasText(props.getWecomWebhookKey());
    }

    @Override
    public void send(SignalResult signal) {
        if (!isReady()) {
            log.warn("企业微信 webhook key 未配置，跳过推送 code={}", signal.getCode());
            return;
        }
        String url = props.getWecomWebhookUrl() + "?key=" + props.getWecomWebhookKey();
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", AlertMessageFormatter.toWeComMarkdown(signal));
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String resp = restTemplate.postForObject(url,
                    new org.springframework.http.HttpEntity<>(body, headers), String.class);
            log.info("企业微信推送 code={} resp={}", signal.getCode(), resp);
        } catch (Exception e) {
            log.error("企业微信推送失败 code={} err={}", signal.getCode(), e.getMessage());
        }
    }

    @Override
    public void sendExit(SignalTradeTrack track) {
        if (!isReady()) {
            throw new IllegalStateException("企业微信 webhook key 未配置");
        }
        String url = props.getWecomWebhookUrl() + "?key=" + props.getWecomWebhookKey();
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", AlertMessageFormatter.exitToWeComMarkdown(track));
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String resp = restTemplate.postForObject(url,
                    new org.springframework.http.HttpEntity<>(body, headers), String.class);
            log.info("企业微信退出通知 signalId={} resp={}", track.getSignalId(), resp);
        } catch (Exception e) {
            throw new IllegalStateException("企业微信退出通知失败: " + e.getMessage(), e);
        }
    }
}
