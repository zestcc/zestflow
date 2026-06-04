package com.zestflow.admin.tenant;

import com.zestflow.admin.config.TenantContextHolder;
import com.zestflow.admin.config.TenantModeConfig;
import com.zestflow.admin.model.entity.DictDataPO;
import com.zestflow.admin.model.entity.DictTypePO;
import com.zestflow.admin.model.entity.RolePO;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.repository.DictDataMapper;
import com.zestflow.admin.repository.DictTypeMapper;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantTemplateClonerTest {

    @Mock private RoleMapper roleMapper;
    @Mock private DictTypeMapper dictTypeMapper;
    @Mock private DictDataMapper dictDataMapper;
    @Mock private PlaygroundSceneMapper playgroundSceneMapper;
    @Mock private ScheduleMapper scheduleMapper;
    @Mock private TenantModeConfig tenantModeConfig;

    private TenantTemplateCloner cloner;

    @BeforeEach
    void setUp() {
        cloner = new TenantTemplateCloner(
                roleMapper, dictTypeMapper, dictDataMapper, playgroundSceneMapper, scheduleMapper, tenantModeConfig);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void cloneFromTemplate_skipsWhenSameTenant() {
        TenantCloneSummary summary = cloner.cloneFromTemplate(1L, 1L);
        assertThat(summary.totalItems()).isZero();
        verifyNoInteractions(roleMapper, dictTypeMapper, dictDataMapper, playgroundSceneMapper, scheduleMapper);
    }

    @Test
    void cloneFromTemplate_copiesAllTenantScopedTables() {
        RolePO role = new RolePO();
        role.setCode("APP_ADMIN");
        role.setName("管理员");
        role.setTenantId(1L);
        when(roleMapper.selectList(any())).thenReturn(List.of(role));

        DictTypePO dictType = new DictTypePO();
        dictType.setCode("route_strategy");
        dictType.setName("路由策略");
        dictType.setTenantId(1L);
        when(dictTypeMapper.selectList(any())).thenReturn(List.of(dictType));

        DictDataPO dictData = new DictDataPO();
        dictData.setTypeCode("route_strategy");
        dictData.setValue("round_robin");
        dictData.setLabel("轮询");
        dictData.setTenantId(1L);
        when(dictDataMapper.selectList(any())).thenReturn(List.of(dictData));

        PlaygroundScenePO scene = new PlaygroundScenePO();
        scene.setSceneCode("SCN001");
        scene.setName("测试");
        scene.setRequestPath("/execute");
        scene.setRequestMethod("POST");
        scene.setBodyType("JSON");
        scene.setChainCode("CHN_TEST");
        scene.setTenantId(1L);
        when(playgroundSceneMapper.selectList(any())).thenReturn(List.of(scene));

        when(scheduleMapper.selectList(any())).thenReturn(List.of());

        TenantCloneSummary summary = cloner.cloneFromTemplate(99L, 1L);

        assertThat(summary.getRoles()).isEqualTo(1);
        assertThat(summary.getDictTypes()).isEqualTo(1);
        assertThat(summary.getDictData()).isEqualTo(1);
        assertThat(summary.getPlaygroundScenes()).isEqualTo(1);
        assertThat(summary.totalItems()).isEqualTo(4);

        verify(roleMapper).insert(any(RolePO.class));
        verify(dictTypeMapper).insert(any(DictTypePO.class));
        verify(dictDataMapper).insert(any(DictDataPO.class));
        verify(playgroundSceneMapper).insert(any(PlaygroundScenePO.class));
    }
}
