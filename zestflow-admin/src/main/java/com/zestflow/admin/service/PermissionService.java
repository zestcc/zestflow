package com.zestflow.admin.service;

import java.util.Set;

public interface PermissionService {

    boolean isSuperAdmin(Long userId);

    Set<String> getAccessibleAppCodes(Long userId);

    boolean hasAppPermission(Long userId, String appCode, String requiredRoleCode);

    String getAppRole(Long userId, String appCode);
}
