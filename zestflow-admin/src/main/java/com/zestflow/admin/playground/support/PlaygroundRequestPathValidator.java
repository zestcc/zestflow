package com.zestflow.admin.playground.support;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 演示场景请求路径校验 — 禁止绝对 URL（防 SSRF），仅允许相对路径
 */
public final class PlaygroundRequestPathValidator {

    /** 允许：/execute、/api/xxx，禁止 ..、//、scheme */
    private static final Pattern ALLOWED = Pattern.compile("^/(execute|api)(/.*)?$");

    private PlaygroundRequestPathValidator() {
    }

    public static void validate(String requestPath) {
        if (!StringUtils.hasText(requestPath)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请求路径不能为空");
        }
        String path = requestPath.trim();
        if (path.contains("://") || path.startsWith("//")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "禁止配置绝对 URL，请使用相对路径如 /api/orders/xxx 或 /execute");
        }
        if (path.contains("..") || path.contains("@")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请求路径非法");
        }
        if (!path.startsWith("/")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请求路径必须以 / 开头");
        }
        if (!ALLOWED.matcher(path).matches()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    "请求路径仅允许 /execute 或 /api/ 前缀");
        }
    }
}
