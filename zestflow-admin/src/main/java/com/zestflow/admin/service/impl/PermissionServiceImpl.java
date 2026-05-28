package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.RolePO;
import com.zestflow.admin.model.entity.UserModuleRolePO;
import com.zestflow.admin.model.entity.UserPO;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.repository.UserMapper;
import com.zestflow.admin.repository.UserModuleRoleMapper;
import com.zestflow.admin.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final Map<String, Integer> ROLE_LEVEL = Map.of(
            "MODULE_ADMIN", 3,
            "MODULE_EDITOR", 2,
            "MODULE_VIEWER", 1
    );

    private final UserMapper userMapper;
    private final UserModuleRoleMapper userModuleRoleMapper;
    private final RoleMapper roleMapper;

    // 角色编码缓存，避免重复查询数据库
    private final Map<Long, String> roleCodeCache = new ConcurrentHashMap<>();

    @Override
    public boolean isSuperAdmin(Long userId) {
        UserPO user = userMapper.selectById(userId);
        return user != null && user.getIsSuperAdmin() != null && user.getIsSuperAdmin() == 1;
    }

    @Override
    public Set<Long> getAccessibleModuleIds(Long userId) {
        if (isSuperAdmin(userId)) {
            return Collections.emptySet();
        }
        List<UserModuleRolePO> list = userModuleRoleMapper.selectList(
                new LambdaQueryWrapper<UserModuleRolePO>()
                        .eq(UserModuleRolePO::getUserId, userId)
        );
        return list.stream().map(UserModuleRolePO::getModuleId).collect(Collectors.toSet());
    }

    @Override
    public boolean hasModulePermission(Long userId, Long moduleId, String requiredRoleCode) {
        if (isSuperAdmin(userId)) {
            return true;
        }

        UserModuleRolePO assignment = userModuleRoleMapper.selectOne(
                new LambdaQueryWrapper<UserModuleRolePO>()
                        .eq(UserModuleRolePO::getUserId, userId)
                        .eq(UserModuleRolePO::getModuleId, moduleId)
                        .last("LIMIT 1")
        );

        if (assignment == null) {
            return false;
        }

        String userRoleCode = getRoleCode(assignment.getRoleId());
        if (userRoleCode == null) return false;

        Integer userLevel = ROLE_LEVEL.get(userRoleCode);
        Integer requiredLevel = ROLE_LEVEL.get(requiredRoleCode);
        if (userLevel == null || requiredLevel == null) {
            return false;
        }
        return userLevel >= requiredLevel;
    }

    @Override
    public String getModuleRole(Long userId, Long moduleId) {
        UserModuleRolePO assignment = userModuleRoleMapper.selectOne(
                new LambdaQueryWrapper<UserModuleRolePO>()
                        .eq(UserModuleRolePO::getUserId, userId)
                        .eq(UserModuleRolePO::getModuleId, moduleId)
                        .last("LIMIT 1")
        );
        if (assignment == null) {
            return null;
        }
        return getRoleCode(assignment.getRoleId());
    }

    private String getRoleCode(Long roleId) {
        return roleCodeCache.computeIfAbsent(roleId, id -> {
            RolePO role = roleMapper.selectById(id);
            return role != null ? role.getCode() : null;
        });
    }
}
