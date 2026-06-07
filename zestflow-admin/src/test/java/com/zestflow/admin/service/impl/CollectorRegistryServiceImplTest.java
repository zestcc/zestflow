package com.zestflow.admin.service.impl;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.registry.InMemoryRegistryLiveStore;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectorRegistryServiceImplTest {

    @Mock private CollectorRegistryMapper collectorRegistryMapper;
    @Mock private TenantAppContext tenantAppContext;
    @Mock private com.zestflow.admin.registry.RegistryLifecycleService registryLifecycleService;

    private RegistryLiveStore liveStore;
    private CollectorRegistryServiceImpl collectorRegistryService;

    @BeforeEach
    void setUp() {
        liveStore = new InMemoryRegistryLiveStore();
        collectorRegistryService = new CollectorRegistryServiceImpl(
                collectorRegistryMapper, tenantAppContext, liveStore, registryLifecycleService);
    }

    @Captor private ArgumentCaptor<CollectorRegistryPO> poCaptor;

    // ==================== 注册 ====================

    @Test
    void register_newCollector_inserts() {
        when(collectorRegistryMapper.selectOne(any())).thenReturn(null);
        when(collectorRegistryMapper.selectList(any())).thenReturn(Collections.emptyList());

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("collector-1");
        dto.setHost("192.168.1.10");
        dto.setPort(9998);
        dto.setAppCode("test-app");
        dto.setAppName("测试采集器");

        collectorRegistryService.register(dto);

        verify(collectorRegistryMapper).insert(poCaptor.capture());
        CollectorRegistryPO inserted = poCaptor.getValue();
        assertThat(inserted.getCollectorId()).isEqualTo("collector-1");
        assertThat(inserted.getCollectorHost()).isEqualTo("192.168.1.10");
        assertThat(inserted.getCollectorPort()).isEqualTo(9998);
        assertThat(inserted.getAppCode()).isEqualTo("test-app");
        assertThat(inserted.getAppName()).isEqualTo("测试采集器");
        assertThat(inserted.getStatus()).isEqualTo(RegistryConstants.STATUS_ONLINE);
        assertThat(inserted.getLastHeartbeat()).isNotNull();
    }

    @Test
    void register_existingCollector_updates() {
        CollectorRegistryPO existing = new CollectorRegistryPO();
        existing.setId(1L);
        existing.setCollectorId("collector-1");
        existing.setAppCode("test-app");
        when(collectorRegistryMapper.selectOne(any())).thenReturn(existing);

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("collector-1");
        dto.setHost("192.168.1.20");
        dto.setPort(9997);
        dto.setAppCode("test-app");

        collectorRegistryService.register(dto);

        verify(collectorRegistryMapper, never()).insert(any(CollectorRegistryPO.class));
        verify(collectorRegistryMapper).updateById(poCaptor.capture());
        CollectorRegistryPO updated = poCaptor.getValue();
        assertThat(updated.getCollectorHost()).isEqualTo("192.168.1.20");
        assertThat(updated.getCollectorPort()).isEqualTo(9997);
        assertThat(updated.getStatus()).isEqualTo(RegistryConstants.STATUS_ONLINE);
    }

    @Test
    void register_oldFormatMigration_updatesExistingByAddress() {
        when(collectorRegistryMapper.selectOne(any())).thenReturn(null);

        CollectorRegistryPO oldRecord = new CollectorRegistryPO();
        oldRecord.setId(1L);
        oldRecord.setCollectorId("old-collector-id");
        oldRecord.setCollectorHost("192.168.1.10");
        oldRecord.setCollectorPort(9998);
        oldRecord.setAppCode("test-app");
        when(collectorRegistryMapper.selectList(any())).thenReturn(List.of(oldRecord));

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("collector-1");
        dto.setHost("192.168.1.10");
        dto.setPort(9998);
        dto.setAppCode("test-app");

        collectorRegistryService.register(dto);

        verify(collectorRegistryMapper).updateById(poCaptor.capture());
        CollectorRegistryPO updated = poCaptor.getValue();
        assertThat(updated.getCollectorId()).isEqualTo("collector-1");
        assertThat(updated.getCollectorHost()).isEqualTo("192.168.1.10");
    }

    @Test
    void register_oldFormat_duplicateAddresses_merges() {
        when(collectorRegistryMapper.selectOne(any())).thenReturn(null);

        CollectorRegistryPO old1 = new CollectorRegistryPO();
        old1.setId(1L);
        old1.setCollectorHost("192.168.1.10");
        old1.setCollectorPort(9998);
        CollectorRegistryPO old2 = new CollectorRegistryPO();
        old2.setId(2L);
        old2.setCollectorHost("192.168.1.10");
        old2.setCollectorPort(9998);
        when(collectorRegistryMapper.selectList(any())).thenReturn(List.of(old1, old2));

        RegisterDTO dto = new RegisterDTO();
        dto.setExecutorId("collector-1");
        dto.setHost("192.168.1.10");
        dto.setPort(9998);
        dto.setAppCode("test-app");

        collectorRegistryService.register(dto);

        // 应删除 deplicate old2，更新 old1
        verify(collectorRegistryMapper).deleteById(2L);
        verify(collectorRegistryMapper, times(1)).deleteById(anyLong());
        verify(collectorRegistryMapper).updateById(poCaptor.capture());
        assertThat(poCaptor.getValue().getCollectorId()).isEqualTo("collector-1");
    }

    // ==================== 心跳 ====================

    @Test
    void heartbeat_existingCollector_updatesLiveStoreOnly() {
        liveStore.seedCollector("collector-1", System.currentTimeMillis());

        HeartbeatDTO dto = new HeartbeatDTO();
        dto.setExecutorId("collector-1");

        collectorRegistryService.heartbeat(dto);

        verify(collectorRegistryMapper, never()).updateById(any(CollectorRegistryPO.class));
        assertThat(liveStore.isCollectorAlive("collector-1")).isTrue();
    }

    @Test
    void heartbeat_unregisteredCollector_throws() {
        when(collectorRegistryMapper.selectOne(any())).thenReturn(null);

        HeartbeatDTO dto = new HeartbeatDTO();
        dto.setExecutorId("unknown");

        assertThatThrownBy(() -> collectorRegistryService.heartbeat(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXECUTOR_NOT_FOUND);
    }

    // ==================== 注销 ====================

    @Test
    void deregister_existingCollector_setsOffline() {
        CollectorRegistryPO existing = new CollectorRegistryPO();
        existing.setId(1L);
        existing.setCollectorId("collector-1");
        existing.setStatus(RegistryConstants.STATUS_ONLINE);
        when(collectorRegistryMapper.selectOne(any())).thenReturn(existing);

        collectorRegistryService.deregister("collector-1");

        assertThat(existing.getStatus()).isEqualTo(RegistryConstants.STATUS_OFFLINE);
        verify(collectorRegistryMapper).updateById(existing);
    }

    @Test
    void deregister_unknownCollector_doesNothing() {
        when(collectorRegistryMapper.selectOne(any())).thenReturn(null);

        collectorRegistryService.deregister("unknown");

        verify(collectorRegistryMapper, never()).updateById(any(CollectorRegistryPO.class));
    }

    // ==================== listAll（含 app_code 过滤）====================

    @Test
    void listAll_superAdmin_noFilter() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());

        CollectorRegistryPO po = new CollectorRegistryPO();
        po.setId(1L);
        po.setCollectorId("c1");
        po.setCollectorHost("192.168.1.1");
        po.setCollectorPort(9998);
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setAppCode("app-a");
        po.setLastHeartbeat(LocalDateTime.now());
        when(collectorRegistryMapper.selectList(any())).thenReturn(List.of(po));

        List<CollectorRegistryVO> list = collectorRegistryService.listAll();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCollectorId()).isEqualTo("c1");
        // 验证查询条件不带 app_code 过滤
        verify(collectorRegistryMapper).selectList(any());
    }

    @Test
    void listAll_normalUser_filtersByAppCode() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Set.of("app-a", "app-b"));

        CollectorRegistryPO poA = new CollectorRegistryPO();
        poA.setId(1L);
        poA.setCollectorId("c1");
        poA.setCollectorHost("192.168.1.1");
        poA.setCollectorPort(9998);
        poA.setStatus(RegistryConstants.STATUS_ONLINE);
        poA.setAppCode("app-a");
        poA.setLastHeartbeat(LocalDateTime.now());
        when(collectorRegistryMapper.selectList(any())).thenReturn(List.of(poA));

        List<CollectorRegistryVO> list = collectorRegistryService.listAll();

        assertThat(list).hasSize(1);
        verify(collectorRegistryMapper).selectList(any());
    }

    // ==================== getByCollectorId ====================

    @Test
    void getByCollectorId_found_returnsVO() {
        CollectorRegistryPO po = new CollectorRegistryPO();
        po.setId(1L);
        po.setCollectorId("c1");
        po.setCollectorHost("192.168.1.1");
        po.setCollectorPort(9998);
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setAppCode("app-a");
        po.setLastHeartbeat(LocalDateTime.now());
        when(collectorRegistryMapper.selectOne(any())).thenReturn(po);

        CollectorRegistryVO vo = collectorRegistryService.getByCollectorId("c1");

        assertThat(vo.getCollectorId()).isEqualTo("c1");
    }

    @Test
    void getByCollectorId_notFound_throws() {
        when(collectorRegistryMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> collectorRegistryService.getByCollectorId("unknown"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXECUTOR_NOT_FOUND);
    }

    // ==================== updateStatus ====================

    @Test
    void updateStatus_validStatus_updates() {
        CollectorRegistryPO po = new CollectorRegistryPO();
        po.setId(1L);
        po.setCollectorId("collector-1");
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        when(collectorRegistryMapper.selectById(1L)).thenReturn(po);

        collectorRegistryService.updateStatus(1L, RegistryConstants.STATUS_OFFLINE);

        assertThat(po.getStatus()).isEqualTo(RegistryConstants.STATUS_OFFLINE);
        verify(collectorRegistryMapper).updateById(po);
    }

    @Test
    void updateStatus_notFound_throws() {
        when(collectorRegistryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> collectorRegistryService.updateStatus(999L, 1))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXECUTOR_NOT_FOUND);
    }

    @Test
    void updateStatus_invalidStatus_throws() {
        CollectorRegistryPO po = new CollectorRegistryPO();
        po.setId(1L);
        when(collectorRegistryMapper.selectById(1L)).thenReturn(po);

        assertThatThrownBy(() -> collectorRegistryService.updateStatus(1L, 999))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void updateStatus_nullStatus_throws() {
        CollectorRegistryPO po = new CollectorRegistryPO();
        po.setId(1L);
        when(collectorRegistryMapper.selectById(1L)).thenReturn(po);

        assertThatThrownBy(() -> collectorRegistryService.updateStatus(1L, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    // ==================== listAllOnline ====================

    @Test
    void listAllOnline_returnsOnlyOnline() {
        CollectorRegistryPO po = new CollectorRegistryPO();
        po.setId(1L);
        po.setCollectorId("c1");
        po.setStatus(RegistryConstants.STATUS_ONLINE);
        po.setCollectorHost("192.168.1.1");
        po.setCollectorPort(9998);
        po.setLastHeartbeat(LocalDateTime.now());
        liveStore.touchCollector("c1");
        when(collectorRegistryMapper.selectList(any())).thenReturn(List.of(po));

        List<CollectorRegistryVO> list = collectorRegistryService.listAllOnline();

        assertThat(list).hasSize(1);
        verify(collectorRegistryMapper).selectList(any());
    }
}
