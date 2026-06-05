package com.zestflow.admin.service.impl;

import com.zestflow.admin.model.dto.TenantCreateDTO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.model.vo.TenantVO;
import com.zestflow.admin.repository.TenantMapper;
import com.zestflow.admin.repository.UserTenantMapper;
import com.zestflow.admin.tenant.ProvisionSources;
import com.zestflow.admin.tenant.TenantCloneSummary;
import com.zestflow.admin.tenant.TenantProvisionResult;
import com.zestflow.admin.tenant.TenantProvisioner;
import com.zestflow.admin.tenant.TenantTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @Mock private TenantMapper tenantMapper;
    @Mock private UserTenantMapper userTenantMapper;
    @Mock private TenantProvisioner tenantProvisioner;

    private TenantServiceImpl tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantServiceImpl(tenantMapper, userTenantMapper, tenantProvisioner);
    }

    @Test
    void listAll_returnsAllTenants() {
        TenantPO po = new TenantPO();
        po.setId(1L);
        po.setName("系统母版");
        po.setCode("system-template");
        po.setStatus(1);
        po.setTenantType(TenantTypes.STANDARD);
        when(tenantMapper.selectList(null)).thenReturn(List.of(po));

        List<TenantVO> result = tenantService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("system-template");
        assertThat(result.get(0).getTenantType()).isEqualTo(TenantTypes.STANDARD);
    }

    @Test
    void create_delegatesToProvisionerAndClonesTemplate() {
        TenantPO created = new TenantPO();
        created.setId(9L);
        created.setName("新客户");
        created.setCode("acme");
        created.setTenantType(TenantTypes.STANDARD);
        created.setProvisionSource(ProvisionSources.ADMIN);

        when(tenantProvisioner.provision(any())).thenReturn(
                TenantProvisionResult.builder()
                        .tenant(created)
                        .cloneSummary(TenantCloneSummary.builder().roles(3).playgroundScenes(28).build())
                        .build());

        TenantCreateDTO dto = new TenantCreateDTO();
        dto.setName("新客户");
        dto.setCode("acme");
        dto.setDescription("测试");

        TenantVO vo = tenantService.create(dto);

        assertThat(vo.getId()).isEqualTo(9L);
        assertThat(vo.getCode()).isEqualTo("acme");

        ArgumentCaptor<com.zestflow.admin.tenant.TenantProvisionRequest> captor =
                ArgumentCaptor.forClass(com.zestflow.admin.tenant.TenantProvisionRequest.class);
        verify(tenantProvisioner).provision(captor.capture());
        assertThat(captor.getValue().getTenantType()).isEqualTo(TenantTypes.STANDARD);
        assertThat(captor.getValue().getProvisionSource()).isEqualTo(ProvisionSources.ADMIN);
    }
}
