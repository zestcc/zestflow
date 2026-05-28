package com.zestflow.admin.util;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import org.springframework.security.core.Authentication;

public class SecurityUtils {

    public static Long getUserId(Authentication authentication) {
        if (authentication == null || authentication.getDetails() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Object details = authentication.getDetails();
        if (details instanceof AuthDetails authDetails) {
            return authDetails.userId();
        }
        return (Long) details;
    }

    public static boolean isSuperAdmin(Authentication authentication) {
        if (authentication == null || authentication.getDetails() == null) {
            return false;
        }
        Object details = authentication.getDetails();
        if (details instanceof AuthDetails authDetails) {
            return authDetails.superAdmin();
        }
        return false;
    }

    public record AuthDetails(Long userId, boolean superAdmin) {
    }
}
