package com.zestflow.admin.service.impl;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.MailService;
import com.zestflow.common.exception.BizException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;

/**
 * SMTP 邮件发送实现。
 * <p>
 * 仅在 {@code zestflow.mail.enabled=true} 时创建。
 * 使用 Spring 标准 JavaMailSender + Thymeleaf 模板引擎渲染 HTML 邮件。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "zestflow.mail.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${zestflow.mail.from-name}")
    private String fromName;

    @Value("${zestflow.mail.base-url}")
    private String baseUrl;

    @Value("${zestflow.mail.reset-password.subject}")
    private String resetSubject;

    @Value("${zestflow.mail.verify-email.subject}")
    private String verifySubject;

    @Override
    public void sendVerificationEmail(String to, String username, String token) {
        String verifyUrl = baseUrl + "/verify-email?token=" + token;
        Context ctx = new Context();
        ctx.setVariable("username", username);
        ctx.setVariable("actionUrl", verifyUrl);
        ctx.setVariable("actionLabel", "验证邮箱");
        ctx.setVariable("expireHours", 24);

        String html = templateEngine.process("verify-email", ctx);
        sendMime(to, verifySubject, html);
        log.info("验证邮件发送成功 to={} username={}", to, username);
    }

    @Override
    public void sendResetPasswordEmail(String to, String username, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        Context ctx = new Context();
        ctx.setVariable("username", username);
        ctx.setVariable("actionUrl", resetUrl);
        ctx.setVariable("actionLabel", "重置密码");
        ctx.setVariable("expireHours", 1);

        String html = templateEngine.process("reset-password-email", ctx);
        sendMime(to, resetSubject, html);
        log.info("重置密码邮件发送成功 to={} username={}", to, username);
    }

    @Override
    public void sendWelcomeEmail(String to, String username, String password) {
        String loginUrl = baseUrl + "/login";
        Context ctx = new Context();
        ctx.setVariable("username", username);
        ctx.setVariable("password", password);
        ctx.setVariable("loginUrl", loginUrl);

        String html = templateEngine.process("welcome-email", ctx);
        sendMime(to, "ZestFlow - 账号开通通知", html);
        log.info("欢迎邮件发送成功 to={} username={}", to, username);
    }

    private void sendMime(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(from, fromName));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("邮件发送失败 to={} subject={}", to, subject, e);
            throw new BizException(ErrorCode.MAIL_SEND_FAILED);
        }
    }
}
