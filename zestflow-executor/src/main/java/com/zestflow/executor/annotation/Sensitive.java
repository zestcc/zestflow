package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记 ChainContext 中的敏感数据字段。
 * <p>
 * 标注了此注解的字段在事件序列化和日志输出时将被自动脱敏处理。
 * <p>
 * 使用方式：
 * <pre>{@code
 * // 在 ChainContext 中存入敏感数据时标记
 * context.put("password", new SensitiveWrapper("secret123"));
 * // 或在事件发布时自动扫描并脱敏
 * }</pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {

    /** 脱敏策略 */
    MaskStrategy strategy() default MaskStrategy.MASK_ALL;

    /** 保留前几位 */
    int keepPrefix() default 0;

    /** 保留后几位 */
    int keepSuffix() default 0;

    /** 脱敏策略枚举 */
    enum MaskStrategy {
        /** 完全遮蔽 */
        MASK_ALL,
        /** 保留首尾 */
        MASK_MIDDLE,
        /** 只保留首部 */
        PREFIX_ONLY,
        /** 只保留尾部 */
        SUFFIX_ONLY,
        /** 手机号脱敏（保留前3后4） */
        PHONE,
        /** 邮箱脱敏 */
        EMAIL,
        /** 身份证号脱敏 */
        ID_CARD
    }
}