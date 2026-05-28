package com.zestflow.admin.service;

import java.util.Set;

public interface PermissionService {

    boolean isSuperAdmin(Long userId);

    Set<Long> getAccessibleModuleIds(Long userId);

    boolean hasModulePermission(Long userId, Long moduleId, String requiredRoleCode);

    String getModuleRole(Long userId, Long moduleId);
}
