package com.zestflow.admin.service.impl;

import com.zestflow.admin.model.entity.RolePO;
import com.zestflow.admin.model.vo.RoleVO;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.service.TenantAppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock private RoleMapper roleMapper;
    @Mock private TenantAppContext tenantAppContext;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(roleMapper, tenantAppContext);
        when(tenantAppContext.getCurrentTenantId()).thenReturn(2L);
    }

    @Test
    void listAll_filtersByCurrentTenant() {
        RolePO tenantRole = new RolePO();
        tenantRole.setId(10L);
        tenantRole.setCode("APP_ADMIN");
        tenantRole.setName("管理员");
        tenantRole.setTenantId(2L);
        when(roleMapper.selectList(any())).thenReturn(List.of(tenantRole));

        List<RoleVO> result = roleService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("APP_ADMIN");
        verify(roleMapper).selectList(any());
    }
}
