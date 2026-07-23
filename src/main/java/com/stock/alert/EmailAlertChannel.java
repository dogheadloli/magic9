package com.stock.alert;

import com.stock.config.AlertProperties;
import com.stock.domain.SignalTradeTrack;
import com.stock.signal.SignalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 邮件预警渠道（SMTP）。
 * SMTP 服务器走标准 spring.mail.*；收件人/发件人走 alert.email-*。
 * 未配置 spring.mail.host 或收件人为空时 isReady()=false，自动跳过，不影响启动。
 */
@Slf4j
@Component
public class EmailAlertChannel implements AlertChannel {

    private final AlertProperties props;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String springMailUsername;
    private final String springMailHost;

    public EmailAlertChannel(AlertProperties props,
                             ObjectProvider<JavaMailSender> mailSenderProvider,
                             @Value("${spring.mail.username:}") String springMailUsername,
                             @Value("${spring.mail.host:}") String springMailHost) {
        this.props = props;
        this.mailSenderProvider = mailSenderProvider;
        this.springMailUsername = springMailUsername;
        this.springMailHost = springMailHost;
    }

    @Override
    public String name() {
        return "EMAIL";
    }

    @Override
    public boolean isReady() {
        return StringUtils.hasText(springMailHost)
                && mailSenderProvider.getIfAvailable() != null
                && !recipients().isEmpty();
    }

    private List<String> recipients() {
        if (props.getEmailTo() == null) {
            return java.util.Collections.emptyList();
        }
        return props.getEmailTo().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    @Override
    public void send(SignalResult signal) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        List<String> to = recipients();
        if (sender == null || !StringUtils.hasText(springMailHost) || to.isEmpty()) {
            log.warn("邮件渠道未就绪（未配置 spring.mail.host 或收件人为空），跳过 code={}", signal.getCode());
            return;
        }
        String from = StringUtils.hasText(props.getEmailFrom()) ? props.getEmailFrom() : springMailUsername;
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(props.getEmailSubjectPrefix() + " " + AlertMessageFormatter.mailSubject(signal));
            helper.setText(AlertMessageFormatter.toHtml(signal), true);
            sender.send(msg);
            log.info("邮件推送成功 code={} 收件人={}", signal.getCode(), to);
        } catch (Exception e) {
            log.error("邮件推送失败 code={} err={}", signal.getCode(), e.getMessage());
        }
    }

    @Override
    public void sendExit(SignalTradeTrack track) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        List<String> to = recipients();
        if (sender == null || !StringUtils.hasText(springMailHost) || to.isEmpty()) {
            throw new IllegalStateException("邮件渠道未就绪");
        }
        String from = StringUtils.hasText(props.getEmailFrom()) ? props.getEmailFrom() : springMailUsername;
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(props.getEmailSubjectPrefix() + " " + AlertMessageFormatter.exitSubject(track));
            helper.setText(AlertMessageFormatter.exitToHtml(track), true);
            sender.send(msg);
            log.info("邮件退出通知成功 signalId={} 收件人={}", track.getSignalId(), to);
        } catch (Exception e) {
            throw new IllegalStateException("邮件退出通知失败: " + e.getMessage(), e);
        }
    }
}
