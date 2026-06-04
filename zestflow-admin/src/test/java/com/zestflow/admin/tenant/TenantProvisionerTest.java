package com.zestflow.admin.tenant;

import com.zestflow.admin.config.TenantContextHolder;
import com.zestflow.admin.config.TenantModeConfig;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.repository.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisionerTest {

    @Mock private TenantMapper tenantMapper;
    @Mock private TenantIpMappingMapper tenantIpMappingMapper;
    @Mock private TenantTemplateCloner templateCloner;
    @Mock private TenantModeConfig tenantModeConfig;

    private TenantProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new TenantProvisioner(tenantMapper, tenantIpMappingMapper, templateCloner, tenantModeConfig);
        lenient().when(tenantModeConfig.getTemplateTenantId()).thenReturn(1L);
        lenient().when(tenantModeConfig.getIpTenantTimeoutMinutes()).thenReturn(60L);
        lenient().when(templateCloner.resolveTemplateTenantId(null)).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void buildIpTenantCode_isDeterministic() {
        assertThat(TenantProvisioner.buildIpTenantCode("203.0.113.7"))
                .startsWith("demo-")
                .isEqualTo(TenantProvisioner.buildIpTenantCode("203.0.113.7"));
    }

    @Test
    void resolveOrProvisionByIp_returnsExistingWithoutInsert() {
        TenantIpMappingPO existing = new TenantIpMappingPO();
        existing.setTenantId(99L);
        existing.setIpAddress("10.0.0.50");
        when(tenantIpMappingMapper.selectOne(any())).thenReturn(existing);

        TenantIpMappingPO result = provisioner.resolveOrProvisionByIp("10.0.0.50");

        assertThat(result.getTenantId()).isEqualTo(99L);
        verify(tenantMapper, never()).insert(any(TenantPO.class));
    }

    @Test
    void resolveOrProvisionByIp_createsTrialTenantAndClonesTemplate() {
        when(tenantIpMappingMapper.selectOne(any())).thenReturn(null);
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            TenantPO po = inv.getArgument(0);
            po.setId(42L);
            return 1;
        }).when(tenantMapper).insert(any(TenantPO.class));

        when(templateCloner.cloneFromTemplate(42L, 1L)).thenReturn(
                TenantCloneSummary.builder().playgroundScenes(1).roles(3).dictTypes(5).build());

        TenantIpMappingPO result = provisioner.resolveOrProvisionByIp("10.0.0.88");

        assertThat(result.getTenantId()).isEqualTo(42L);
        assertThat(result.getIpAddress()).isEqualTo("10.0.0.88");

        ArgumentCaptor<TenantPO> tenantCaptor = ArgumentCaptor.forClass(TenantPO.class);
        verify(tenantMapper).insert(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getTenantType()).isEqualTo(TenantTypes.TRIAL);
        assertThat(tenantCaptor.getValue().getProvisionSource()).isEqualTo(ProvisionSources.IP);
        assertThat(tenantCaptor.getValue().getExpiresAt()).isNotNull();

        verify(templateCloner).cloneFromTemplate(42L, 1L);
    }

    @Test
    void provision_standardTenant_clonesTemplate() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            TenantPO po = inv.getArgument(0);
            po.setId(5L);
            return 1;
        }).when(tenantMapper).insert(any(TenantPO.class));
        when(templateCloner.cloneFromTemplate(5L, 1L)).thenReturn(
                TenantCloneSummary.builder().roles(3).build());

        TenantProvisionResult result = provisioner.provision(TenantProvisionRequest.builder()
                .name("正式客户")
                .code("acme")
                .tenantType(TenantTypes.STANDARD)
                .provisionSource(ProvisionSources.ADMIN)
                .createdBy("admin")
                .build());

        assertThat(result.getTenant().getId()).isEqualTo(5L);
        assertThat(result.getItemsCloned()).isEqualTo(3);
        verify(templateCloner).cloneFromTemplate(5L, 1L);
    }

    @Test
    void provision_apiTrial_clonesTemplate() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            TenantPO po = inv.getArgument(0);
            po.setId(8L);
            return 1;
        }).when(tenantMapper).insert(any(TenantPO.class));
        when(templateCloner.cloneFromTemplate(8L, 1L)).thenReturn(
                TenantCloneSummary.builder().playgroundScenes(28).build());

        TenantProvisionResult result = provisioner.provision(TenantProvisionRequest.builder()
                .name("API试玩")
                .code("trial-api1")
                .tenantType(TenantTypes.TRIAL)
                .provisionSource(ProvisionSources.API)
                .ttl(Duration.ofMinutes(30))
                .createdBy("public-api")
                .build());

        assertThat(result.getScenesCloned()).isEqualTo(28);
        verify(templateCloner).cloneFromTemplate(eq(8L), eq(1L));
    }
}
