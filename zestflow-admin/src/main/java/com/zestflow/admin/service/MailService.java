package com.zestflow.admin.service;

/**
 * 邮件发送防腐层接口。
 * <p>
 * 隔离 Spring Mail / JavaMailSender 依赖，业务代码只依赖此抽象。
 * 开关关闭时由 NoopMailService 兜底（只打日志不发送），开关开启时由 SmtpMailService 真实发送。
 * 如需替换邮件服务商（SendGrid / AWS SES / 阿里云邮件），只需新增实现类即可。
 */
public interface MailService {

    /**
     * 发送注册验证邮件
     *
     * @param to      收件人邮箱
     * @param username 用户名
     * @param token   验证令牌
     */
    void sendVerificationEmail(String to, String username, String token);

    /**
     * 发送密码重置邮件
     *
     * @param to      收件人邮箱
     * @param username 用户名
     * @param token   重置令牌
     */
    void sendResetPasswordEmail(String to, String username, String token);

    /**
     * 发送新用户欢迎邮件（含账号密码）
     *
     * @param to      收件人邮箱
     * @param username 用户名
     * @param password 明文密码
     */
    void sendWelcomeEmail(String to, String username, String password);
}
