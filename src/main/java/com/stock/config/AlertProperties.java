package com.stock.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 预警参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "alert")
public class AlertProperties {

    /** 启用的通知渠道：WECOM / CONSOLE */
    private List<String> channels = new ArrayList<>();

    /** 企业微信群机器人 webhook key（放环境变量 WECOM_KEY，勿入库/入仓） */
    private String wecomWebhookKey = "";

    /** 企业微信 webhook 基础地址 */
    private String wecomWebhookUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send";

    /** 同一标的同向冷却天数（此天数内不重复推送） */
    private int cooldownDays = 1;

    /** 邮件发件人地址（留空则用 spring.mail.username） */
    private String emailFrom = "";

    /** 邮件收件人列表（可多个） */
    private List<String> emailTo = new ArrayList<>();

    /** 邮件标题前缀 */
    private String emailSubjectPrefix = "[选股预警]";
}
