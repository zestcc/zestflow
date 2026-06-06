package com.zestflow.executor.security;

import com.zestflow.common.constant.ChainConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具。
 * <p>
 * 对 DataBus 中的敏感字段进行自动脱敏处理，
 * 防止敏感信息泄露到事件日志和持久化存储中。
 */
@Slf4j
public final class SensitiveDataMasker {

    /** 默认敏感字段名模式（不区分大小写） */
    private static final Set<String> DEFAULT_SENSITIVE_KEYS = Set.of(
            "password", "pwd", "secret", "token", "accessToken", "refreshToken",
            "apiKey", "privateKey", "credential", "authorization",
            "idCard", "idNumber", "phone", "mobile", "email",
            "bankCard", "bankAccount", "cvv", "ssn"
    );

    /** 脱敏替换字符 */
    private static final String MASK_CHAR = "****";

    private SensitiveDataMasker() {}

    /**
     * 对 DataBus 快照进行脱敏处理，返回新的 Map。
     */
    public static Map<String, Object> mask(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return snapshot;
        }
        Map<String, Object> masked = new java.util.HashMap<>(snapshot);
        for (String key : masked.keySet()) {
            if (isSensitiveKey(key)) {
                Object value = masked.get(key);
                if (value != null) {
                    masked.put(key, maskValue(key, value));
                }
            }
        }
        return masked;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        for (String sensitiveKey : DEFAULT_SENSITIVE_KEYS) {
            if (lower.contains(sensitiveKey.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static Object maskValue(String key, Object value) {
        if (value instanceof String str) {
            return maskString(str);
        }
        return MASK_CHAR;
    }

    private static String maskString(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int len = value.length();
        if (len <= 4) {
            return MASK_CHAR;
        }
        // 保留首1位 + **** + 尾1位
        return value.charAt(0) + MASK_CHAR + value.charAt(len - 1);
    }

    /**
     * 检查链中是否包含敏感数据（用于日志警告）
     */
    public static boolean containsSensitiveData(Map<String, Object> snapshot) {
        if (snapshot == null) return false;
        return snapshot.keySet().stream().anyMatch(SensitiveDataMasker::isSensitiveKey);
    }
}