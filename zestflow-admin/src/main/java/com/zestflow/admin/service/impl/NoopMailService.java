package com.zestflow.admin.service.impl;

import com.zestflow.admin.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * 邮件未启用时的兜底实现。
 * <p>
 * 仅在 {@code MailService} 无其他 Bean 时创建（即 {@code zestflow.mail.enabled=false}）。
 * 所有方法只记录日志，不执行真实发送。
 */
@Slf4j
@Service
@ConditionalOnMissingBean(MailService.class)
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
