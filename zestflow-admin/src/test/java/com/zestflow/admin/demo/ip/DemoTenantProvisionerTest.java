package com.zestflow.admin.demo.ip;

import com.zestflow.admin.config.TenantContextHolder;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.repository.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoTenantProvisionerTest {

    @Mock private TenantMapper tenantMapper;
    @Mock private TenantIpMappingMapper tenantIpMappingMapper;
    @Mock private PlaygroundSceneMapper playgroundSceneMapper;
    @Mock private TransactionTemplate transactionTemplate;

    private DemoTenantProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new DemoTenantProvisioner(
                tenantMapper, tenantIpMappingMapper, playgroundSceneMapper, transactionTemplate);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void buildTenantCode_isDeterministic() {
        assertThat(DemoTenantProvisioner.buildTenantCode("203.0.113.7"))
                .isEqualTo(DemoTenantProvisioner.buildTenantCode("203.0.113.7"))
                .startsWith("demo-");
    }

    @Test
    void resolveOrProvision_returnsExistingWithoutInsert() {
        TenantIpMappingPO existing = new TenantIpMappingPO();
        existing.setTenantId(99L);
        existing.setIpAddress("10.0.0.50");
        when(tenantIpMappingMapper.selectOne(any())).thenReturn(existing);

        TenantIpMappingPO result = provisioner.resolveOrProvision("10.0.0.50");

        assertThat(result.getTenantId()).isEqualTo(99L);
        verify(tenantMapper, never()).insert(any(TenantPO.class));
    }

    @Test
    void resolveOrProvision_createsTenantAndClonesScenes() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        when(tenantIpMappingMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            TenantPO po = inv.getArgument(0);
            po.setId(42L);
            return 1;
        }).when(tenantMapper).insert(any(TenantPO.class));

        PlaygroundScenePO template = new PlaygroundScenePO();
        template.setSceneCode("SCN20260531000001");
        template.setName("单节点");
        template.setRequestPath("/execute");
        template.setRequestMethod("POST");
        template.setBodyType("JSON");
        template.setChainCode("CHN_DEMO_NODE_1");
        template.setAppCode(DemoTenantProvisioner.DEMO_APP_CODE);
        template.setTenantId(DemoTenantProvisioner.TEMPLATE_TENANT_ID);
        when(playgroundSceneMapper.selectList(any())).thenReturn(List.of(template));

        TenantIpMappingPO result = provisioner.resolveOrProvision("10.0.0.88");

        assertThat(result.getTenantId()).isEqualTo(42L);
        assertThat(result.getIpAddress()).isEqualTo("10.0.0.88");

        ArgumentCaptor<PlaygroundScenePO> sceneCaptor = ArgumentCaptor.forClass(PlaygroundScenePO.class);
        verify(playgroundSceneMapper).insert(sceneCaptor.capture());
        assertThat(sceneCaptor.getValue().getTenantId()).isEqualTo(42L);
        assertThat(sceneCaptor.getValue().getSceneCode()).isEqualTo("SCN20260531000001");
    }
}
