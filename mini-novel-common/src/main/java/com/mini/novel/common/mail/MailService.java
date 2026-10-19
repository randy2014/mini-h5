package com.mini.novel.common.mail;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台邮件通知（充值确认 / 新工单）。
 *
 * 设计要点：
 * - 异步发送（@Async），绝不阻塞业务事务；
 * - 任何异常都只记录日志、不向上抛，邮件失败不影响充值/工单主流程；
 * - 用 ObjectProvider 延迟获取 JavaMailSender：未配置 spring.mail 的应用（如爬虫服务）
 *   也能正常启动，不会因缺少邮件 Bean 而启动失败；
 * - 未配置 MAIL_PASSWORD（QQ 邮箱授权码）时自动跳过。
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String to;
    private final String from;
    private final String password;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${app.mail.enabled:true}") boolean enabled,
                       @Value("${app.mail.to:}") String to,
                       @Value("${app.mail.from:}") String from,
                       @Value("${spring.mail.password:}") String password) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.to = to;
        this.from = from;
        this.password = password;
    }

    @Async
    public void sendRechargeConfirmed(String orderNo, long coins, String amountYuan, String userLabel) {
        send("【Mini Novel】充值到账通知",
                "订单号：" + orderNo
                        + "\n用户：" + userLabel
                        + "\n金额：¥" + amountYuan
                        + "\n到账快乐币：" + coins
                        + "\n时间：" + LocalDateTime.now());
    }

    @Async
    public void sendNewTicket(Long ticketId, String title, String content) {
        send("【Mini Novel】新工单通知",
                "工单编号：" + ticketId
                        + "\n标题：" + title
                        + "\n内容：" + content
                        + "\n时间：" + LocalDateTime.now());
    }

    private void send(String subject, String body) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(password)) {
            log.warn("mail skipped (MAIL_PASSWORD not configured): subject={}", subject);
            return;
        }
        if (!StringUtils.hasText(to)) {
            log.warn("mail skipped (app.mail.to is empty): subject={}", subject);
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("mail skipped (no JavaMailSender bean): subject={}", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (StringUtils.hasText(from)) {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("mail sent: subject={}, to={}", subject, to);
        } catch (Exception ex) {
            log.warn("mail send failed: subject={}, to={}, error={}", subject, to, ex.getMessage());
        }
    }
}
