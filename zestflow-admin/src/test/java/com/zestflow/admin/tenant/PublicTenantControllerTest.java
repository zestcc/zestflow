package com.zestflow.admin.tenant;

import com.zestflow.admin.config.TenantModeConfig;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.tenant.dto.PublicTenantProvisionDTO;
import com.zestflow.admin.tenant.vo.TenantProvisionVO;
import com.zestflow.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicTenantControllerTest {

    @Mock private TenantProvisioner tenantProvisioner;
    @Mock private TenantModeConfig tenantModeConfig;
    @Mock private HttpServletRequest request;

    private PublicTenantController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicTenantController(tenantProvisioner, tenantModeConfig);
        when(tenantModeConfig.getMode()).thenReturn("multi");
        when(tenantModeConfig.getIpTenantTimeoutMinutes()).thenReturn(60L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void provision_returnsCloneSummary() {
        TenantPO tenant = new TenantPO();
        tenant.setId(100L);
        tenant.setCode("trial-abc12345");
        tenant.setName("试玩租户-trial-abc12345");
        tenant.setTenantType(TenantTypes.TRIAL);
        tenant.setProvisionSource(ProvisionSources.API);

        when(tenantProvisioner.provision(any())).thenReturn(
                TenantProvisionResult.builder()
                        .tenant(tenant)
                        .cloneSummary(TenantCloneSummary.builder()
                                .roles(3)
                                .dictTypes(5)
                                .dictData(20)
                                .playgroundScenes(28)
                                .schedules(0)
                                .build())
                        .build());

        PublicTenantProvisionDTO dto = new PublicTenantProvisionDTO();
        dto.setCode("trial-abc12345");
        dto.setName("API试玩");

        Result<TenantProvisionVO> result = controller.provision(dto, request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getItemsCloned()).isEqualTo(56);
        assertThat(result.getData().getPlaygroundScenes()).isEqualTo(28);
        verify(tenantProvisioner).provision(any());
    }
}
