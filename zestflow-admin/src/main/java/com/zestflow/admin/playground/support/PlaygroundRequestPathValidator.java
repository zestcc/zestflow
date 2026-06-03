package com.zestflow.admin.playground.support;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * 演示场景请求路径校验
 * <p>
 * 默认相对路径；仅 Executor 配置 Tomcat 基址时允许对应完整 URL。
 */
public final class PlaygroundRequestPathValidator {

    private static final Pattern ALLOWED_RELATIVE = Pattern.compile("^/(execute|api)(/.*)?$");

    private PlaygroundRequestPathValidator() {
    }

    public static void validate(String requestPath, Collection<String> allowedTomcatBaseUrls) {
        if (!StringUtils.hasText(requestPath)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请求路径不能为空");
        }
        String path = requestPath.trim();
        if (path.contains("..") || path.contains("@")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请求路径非法");
        }
        if (path.contains("://") || path.startsWith("//")) {
            validateTomcatAbsoluteUrl(path, allowedTomcatBaseUrls);
            return;
        }
        validateRelativePath(path);
    }

    public static void validate(String requestPath) {
        validate(requestPath, java.util.List.of());
    }

    private static void validateTomcatAbsoluteUrl(String path, Collection<String> allowedTomcatBaseUrls) {
        if (allowedTomcatBaseUrls == null || allowedTomcatBaseUrls.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    "未配置 Executor playground.url 时请使用相对路径，如 /api/orders/xxx");
        }
        String normalized = path.split("#")[0];
        boolean prefixOk = allowedTomcatBaseUrls.stream().anyMatch(base -> {
            String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            return normalized.equals(b) || normalized.startsWith(b + "/");
        });
        if (!prefixOk) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请求 URL 不在允许的 Tomcat 基址范围内");
        }
        try {
            String rel = URI.create(normalized).getPath();
            validateRelativePath(rel != null ? rel : "");
        } catch (Exception e) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请求 URL 格式非法");
        }
    }

    private static void validateRelativePath(String path) {
        if (!path.startsWith("/")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请求路径必须以 / 开头");
        }
        if (!ALLOWED_RELATIVE.matcher(path).matches()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    "请求路径仅允许 /execute 或 /api/ 前缀");
        }
    }
}
