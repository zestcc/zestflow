package com.zestflow.admin.util;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getPrincipal() instanceof String s ? s : null;
    }

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

    public record AuthDetails(Long userId, boolean superAdmin, Long currentTenantId) {
    }

    public static Long getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getDetails() == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof AuthDetails authDetails) {
            return authDetails.currentTenantId();
        }
        return null;
    }
}
