package com.zestflow.admin.service.impl;

import com.zestflow.admin.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 邮件未启用时的兜底实现。
 * <p>
 * {@code zestflow.mail.enabled=false}（或未配置）时创建，只记录日志不发送。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "zestflow.mail.enabled", havingValue = "false", matchIfMissing = true)
public class NoopMailService implements MailService {

    @Override
    public void sendVerificationEmail(String to, String username, String token) {
        log.info("[邮件未启用] 验证邮件: to={} username={} token={}", to, username, token);
    }

    @Override
    public void sendResetPasswordEmail(String to, String username, String token) {
        log.info("[邮件未启用] 重置密码邮件: to={} username={} token={}", to, username, token);
    }

    @Override
    public void sendWelcomeEmail(String to, String username, String password) {
        log.info("[邮件未启用] 欢迎邮件: to={} username={} password={}", to, username, password);
    }
}
