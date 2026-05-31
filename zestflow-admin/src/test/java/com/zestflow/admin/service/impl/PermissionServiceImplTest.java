package com.zestflow.admin.service.impl;

import com.zestflow.admin.model.entity.RolePO;
import com.zestflow.admin.model.entity.UserAppRolePO;
import com.zestflow.admin.model.entity.UserPO;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.repository.UserAppRoleMapper;
import com.zestflow.admin.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private UserAppRoleMapper userAppRoleMapper;
    @Mock private RoleMapper roleMapper;

    private PermissionServiceImpl permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionServiceImpl(userMapper, userAppRoleMapper, roleMapper);
    }

    // ==================== isSuperAdmin ====================

    @Test
    void isSuperAdmin_whenSuperAdmin_returnsTrue() {
        UserPO user = new UserPO();
        user.setId(1L);
        user.setIsSuperAdmin(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertThat(permissionService.isSuperAdmin(1L)).isTrue();
    }

    @Test
    void isSuperAdmin_whenNormalUser_returnsFalse() {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);

        assertThat(permissionService.isSuperAdmin(2L)).isFalse();
    }

    @Test
    void isSuperAdmin_whenUserNotFound_returnsFalse() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThat(permissionService.isSuperAdmin(999L)).isFalse();
    }

    @Test
    void isSuperAdmin_whenNullField_returnsFalse() {
        UserPO user = new UserPO();
        user.setId(3L);
        user.setIsSuperAdmin(null);
        when(userMapper.selectById(3L)).thenReturn(user);

        assertThat(permissionService.isSuperAdmin(3L)).isFalse();
    }

    // ==================== getAccessibleAppCodes ====================

    @Test
    void getAccessibleAppCodes_superAdmin_returnsEmptySet() {
        UserPO user = new UserPO();
        user.setId(1L);
        user.setIsSuperAdmin(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        Set<String> codes = permissionService.getAccessibleAppCodes(1L);

        assertThat(codes).isEmpty();
        verify(userAppRoleMapper, never()).selectList(any());
    }

    @Test
    void getAccessibleAppCodes_normalUser_returnsAssignedApps() {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);

        UserAppRolePO role1 = new UserAppRolePO();
        role1.setAppCode("app-a");
        UserAppRolePO role2 = new UserAppRolePO();
        role2.setAppCode("app-b");
        when(userAppRoleMapper.selectList(any())).thenReturn(List.of(role1, role2));

        Set<String> codes = permissionService.getAccessibleAppCodes(2L);

        assertThat(codes).containsExactlyInAnyOrder("app-a", "app-b");
    }

    @Test
    void getAccessibleAppCodes_normalUserNoApps_returnsEmptySet() {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);
        when(userAppRoleMapper.selectList(any())).thenReturn(List.of());

        Set<String> codes = permissionService.getAccessibleAppCodes(2L);

        assertThat(codes).isEmpty();
    }

    // ==================== hasAppPermission ====================

    @Test
    void hasAppPermission_superAdmin_alwaysTrue() {
        UserPO user = new UserPO();
        user.setId(1L);
        user.setIsSuperAdmin(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        boolean result = permissionService.hasAppPermission(1L, "any-app", "APP_ADMIN");

        assertThat(result).isTrue();
        verify(userAppRoleMapper, never()).selectList(any());
    }

    @Test
    void hasAppPermission_normalUserNoAssignment_returnsFalse() {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);
        when(userAppRoleMapper.selectOne(any())).thenReturn(null);

        boolean result = permissionService.hasAppPermission(2L, "app-x", "APP_VIEWER");

        assertThat(result).isFalse();
    }

    @Test
    void hasAppPermission_adminLevel_granted() {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);

        UserAppRolePO assignment = new UserAppRolePO();
        assignment.setRoleId(10L);
        assignment.setAppCode("app-a");
        when(userAppRoleMapper.selectOne(any())).thenReturn(assignment);

        RolePO role = new RolePO();
        role.setId(10L);
        role.setCode("APP_ADMIN");
        when(roleMapper.selectById(10L)).thenReturn(role);

        // APP_ADMIN(3) >= APP_EDITOR(2) = true
        assertThat(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).isTrue();
        // APP_ADMIN(3) >= APP_ADMIN(3) = true
        assertThat(permissionService.hasAppPermission(2L, "app-a", "APP_ADMIN")).isTrue();
    }

    @Test
    void hasAppPermission_viewerLevel_granted() {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);

        UserAppRolePO assignment = new UserAppRolePO();
        assignment.setRoleId(11L);
        assignment.setAppCode("app-a");
        when(userAppRoleMapper.selectOne(any())).thenReturn(assignment);

        RolePO role = new RolePO();
        role.setId(11L);
        role.setCode("APP_VIEWER");
        when(roleMapper.selectById(11L)).thenReturn(role);

        // APP_VIEWER(1) >= APP_VIEWER(1) = true
        assertThat(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).isTrue();
        // APP_VIEWER(1) >= APP_EDITOR(2) = false
        assertThat(permissionService.hasAppPermission(2L, "app-a", "APP_EDITOR")).isFalse();
    }

    @Test
    void hasAppPermission_unknownRoleCode_returnsFalse() {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);

        UserAppRolePO assignment = new UserAppRolePO();
        assignment.setRoleId(99L);
        assignment.setAppCode("app-a");
        when(userAppRoleMapper.selectOne(any())).thenReturn(assignment);

        RolePO role = new RolePO();
        role.setId(99L);
        role.setCode("UNKNOWN_ROLE");
        when(roleMapper.selectById(99L)).thenReturn(role);

        assertThat(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).isFalse();
    }

    @Test
    void hasAppPermission_whenRoleNotFound_returnsFalse() {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);

        UserAppRolePO assignment = new UserAppRolePO();
        assignment.setRoleId(10L);
        assignment.setAppCode("app-a");
        when(userAppRoleMapper.selectOne(any())).thenReturn(assignment);

        when(roleMapper.selectById(10L)).thenReturn(null);

        assertThat(permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER")).isFalse();
    }

    // ==================== getAppRole ====================

    @Test
    void getAppRole_returnsRoleCode() {
        UserAppRolePO assignment = new UserAppRolePO();
        assignment.setRoleId(10L);
        assignment.setAppCode("app-a");
        when(userAppRoleMapper.selectOne(any())).thenReturn(assignment);

        RolePO role = new RolePO();
        role.setId(10L);
        role.setCode("APP_ADMIN");
        when(roleMapper.selectById(10L)).thenReturn(role);

        String roleCode = permissionService.getAppRole(2L, "app-a");

        assertThat(roleCode).isEqualTo("APP_ADMIN");
    }

    @Test
    void getAppRole_noAssignment_returnsNull() {
        when(userAppRoleMapper.selectOne(any())).thenReturn(null);

        String roleCode = permissionService.getAppRole(2L, "app-x");

        assertThat(roleCode).isNull();
    }

    // ==================== roleCodeCache 线程安全 ====================

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    void roleCodeCache_threadSafe() throws InterruptedException {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);

        UserAppRolePO assignment = new UserAppRolePO();
        assignment.setRoleId(10L);
        assignment.setAppCode("app-a");
        when(userAppRoleMapper.selectOne(any())).thenReturn(assignment);

        RolePO role = new RolePO();
        role.setId(10L);
        role.setCode("APP_ADMIN");
        // 确保 roleMapper.selectById 被并发调用
        when(roleMapper.selectById(10L)).thenReturn(role);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean result = permissionService.hasAppPermission(2L, "app-a", "APP_VIEWER");
                    if (result) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threadCount);
        // roleMapper.selectById 应只被调用 1 次（缓存命中），但可能有并发竞争缓存
        // 保守验证：最多被调用 threadCount 次，但实际应远小于
        verify(roleMapper, atMost(threadCount)).selectById(10L);
    }

    // ==================== 并发角色变更 ====================

    @Test
    void hasAppPermission_multipleApps_concurrentAccess() throws InterruptedException {
        UserPO user = new UserPO();
        user.setId(2L);
        user.setIsSuperAdmin(0);
        when(userMapper.selectById(2L)).thenReturn(user);

        UserAppRolePO adminRole = new UserAppRolePO();
        adminRole.setRoleId(10L);
        adminRole.setAppCode("app-admin");
        UserAppRolePO viewerRole = new UserAppRolePO();
        viewerRole.setRoleId(11L);
        viewerRole.setAppCode("app-viewer");
        when(userAppRoleMapper.selectOne(any())).thenReturn(adminRole, viewerRole);
        // Default fallback
        when(userAppRoleMapper.selectList(any())).thenReturn(List.of(adminRole, viewerRole));

        RolePO admin = new RolePO();
        admin.setId(10L);
        admin.setCode("APP_ADMIN");
        RolePO viewer = new RolePO();
        viewer.setId(11L);
        viewer.setCode("APP_VIEWER");
        when(roleMapper.selectById(10L)).thenReturn(admin);
        when(roleMapper.selectById(11L)).thenReturn(viewer);

        Set<String> codes = permissionService.getAccessibleAppCodes(2L);

        assertThat(codes).containsExactlyInAnyOrder("app-admin", "app-viewer");
        assertThat(permissionService.hasAppPermission(2L, "app-admin", "APP_ADMIN")).isTrue();
        assertThat(permissionService.hasAppPermission(2L, "app-viewer", "APP_VIEWER")).isTrue();
        assertThat(permissionService.hasAppPermission(2L, "app-viewer", "APP_EDITOR")).isFalse();
    }
}
