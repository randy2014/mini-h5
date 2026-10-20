package com.mini.novel.common.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台邮件通知（充值待审批 / 新工单）。
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

    /** 充值待审批邮件（HTML，含「点击审批通过」按钮）。 */
    @Async
    public void sendRechargePending(String orderNo, long coins, String amountYuan, String userLabel,
                                    String paidHintAt, String approveUrl) {
        String html = "<div style=\"font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#1f2528\">"
                + "<h2 style=\"margin:0 0 12px;font-size:20px\">充值待审批</h2>"
                + "<p style=\"margin:0 0 16px;color:#555;line-height:1.6\">用户 <b>" + escape(userLabel) + "</b> 提交了一笔充值，待你确认到账。</p>"
                + "<table style=\"width:100%;border-collapse:collapse;margin:8px 0 20px;font-size:14px\">"
                + "<tr><td style=\"padding:7px 0;color:#888;width:96px\">订单号</td><td style=\"padding:7px 0;word-break:break-all\">" + escape(orderNo) + "</td></tr>"
                + "<tr><td style=\"padding:7px 0;color:#888\">金额</td><td style=\"padding:7px 0\">¥" + escape(amountYuan) + "</td></tr>"
                + "<tr><td style=\"padding:7px 0;color:#888\">到账快乐币</td><td style=\"padding:7px 0\">" + coins + "</td></tr>"
                + "<tr><td style=\"padding:7px 0;color:#888\">用户已付款</td><td style=\"padding:7px 0\">" + escape(paidHintAt) + "</td></tr>"
                + "</table>"
                + "<p style=\"text-align:center;margin:28px 0\">"
                + "<a href=\"" + escape(approveUrl) + "\" style=\"display:inline-block;padding:13px 34px;background:#1f6f64;color:#ffffff;border-radius:10px;text-decoration:none;font-weight:700;font-size:15px\">点击审批通过</a>"
                + "</p>"
                + "<p style=\"color:#999;font-size:12px;line-height:1.6;word-break:break-all\">若按钮无法点击，请复制以下链接到浏览器打开：<br>" + escape(approveUrl) + "</p>"
                + "</div>";
        sendHtml("【Mini Novel】充值待审批", html);
    }

    @Async
    public void sendNewTicket(Long ticketId, String title, String content) {
        sendText("【Mini Novel】新工单通知",
                "工单编号：" + ticketId
                        + "\n标题：" + title
                        + "\n内容：" + content
                        + "\n时间：" + java.time.LocalDateTime.now());
    }

    private void sendText(String subject, String body) {
        JavaMailSender mailSender = ready(subject);
        if (mailSender == null) {
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

    private void sendHtml(String subject, String html) {
        JavaMailSender mailSender = ready(subject);
        if (mailSender == null) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("mail sent: subject={}, to={}", subject, to);
        } catch (Exception ex) {
            log.warn("mail send failed: subject={}, to={}, error={}", subject, to, ex.getMessage());
        }
    }

    /** 统一前置检查，返回可用的 JavaMailSender；未就绪时返回 null（跳过发送）。 */
    private JavaMailSender ready(String subject) {
        if (!enabled) {
            return null;
        }
        if (!StringUtils.hasText(password)) {
            log.warn("mail skipped (MAIL_PASSWORD not configured): subject={}", subject);
            return null;
        }
        if (!StringUtils.hasText(to)) {
            log.warn("mail skipped (app.mail.to is empty): subject={}", subject);
            return null;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("mail skipped (no JavaMailSender bean): subject={}", subject);
            return null;
        }
        return mailSender;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
