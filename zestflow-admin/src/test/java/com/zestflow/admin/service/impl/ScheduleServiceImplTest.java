package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.ScheduleCreateDTO;
import com.zestflow.admin.model.dto.ScheduleUpdateDTO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.model.vo.ScheduleLogVO;
import com.zestflow.admin.model.vo.ScheduleVO;
import com.zestflow.admin.registry.InMemoryRegistryLiveStore;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import com.zestflow.admin.schedule.ExecutorClient;
import com.zestflow.admin.schedule.RouteStrategy;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceImplTest {

    @Mock private ScheduleMapper scheduleMapper;
    @Mock private ScheduleLogMapper scheduleLogMapper;
    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private ExecutorClient executorClient;
    @Mock private RouteStrategy routeStrategy;
    @Mock private TenantAppContext tenantAppContext;

    private RegistryLiveStore liveStore;
    private ScheduleServiceImpl scheduleService;

    @BeforeEach
    void setUp() {
        liveStore = new InMemoryRegistryLiveStore();
        when(routeStrategy.name()).thenReturn("round_robin");
        scheduleService = new ScheduleServiceImpl(
                scheduleMapper, scheduleLogMapper,
                executorRegistryMapper, liveStore, executorClient,
                tenantAppContext, List.of(routeStrategy)
        );
    }

    // ==================== CRUD ====================

    @Test
    void createSchedule() {
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Set.of("app-a"));

        ScheduleCreateDTO dto = new ScheduleCreateDTO();
        dto.setChainCode("chain-test");
        dto.setChainName("测试链");
        dto.setCron("0 */5 * * * ?");
        dto.setRouteStrategy("round_robin");
        dto.setRemark("测试调度");

        ScheduleVO vo = scheduleService.create(dto, "admin");

        assertThat(vo.getChainCode()).isEqualTo("chain-test");
        assertThat(vo.getCron()).isEqualTo("0 */5 * * * ?");
        assertThat(vo.getCreatedBy()).isEqualTo("admin");
        verify(scheduleMapper).insert(org.mockito.ArgumentMatchers.<SchedulePO>argThat(po ->
                po.getTenantId() == 1L && "app-a".equals(po.getAppCode())));
    }

    @Test
    void createSchedule_noAppCodeAssigned() {
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());

        ScheduleCreateDTO dto = new ScheduleCreateDTO();
        dto.setChainCode("chain-test");
        dto.setChainName("测试链");
        dto.setCron("0 */5 * * * ?");

        ScheduleVO vo = scheduleService.create(dto, "admin");

        assertThat(vo.getChainCode()).isEqualTo("chain-test");
        // appCode 应为 null（超管创建时无显式 appCode）
        verify(scheduleMapper).insert(org.mockito.ArgumentMatchers.<SchedulePO>argThat(po ->
                po.getAppCode() == null && po.getTenantId() == 1L));
    }

    @Test
    void getById() {
        SchedulePO po = new SchedulePO();
        po.setId(1L);
        po.setChainCode("chain-test");
        po.setCron("0 */5 * * * ?");
        po.setStatus(1);
        when(scheduleMapper.selectById(1L)).thenReturn(po);

        ScheduleVO vo = scheduleService.getById(1L);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getChainCode()).isEqualTo("chain-test");
    }

    @Test
    void getById_notFound() {
        when(scheduleMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> scheduleService.getById(999L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    void updateSchedule() {
        SchedulePO po = new SchedulePO();
        po.setId(1L);
        po.setCron("0 */5 * * * ?");
        po.setRouteStrategy("round_robin");
        when(scheduleMapper.selectById(1L)).thenReturn(po);

        ScheduleUpdateDTO dto = new ScheduleUpdateDTO();
        dto.setCron("0 */10 * * * ?");
        dto.setRemark("更新备注");

        ScheduleVO vo = scheduleService.update(1L, dto);

        assertThat(vo.getCron()).isEqualTo("0 */10 * * * ?");
        verify(scheduleMapper).updateById(argThat((SchedulePO sp) ->
                sp.getCron().equals("0 */10 * * * ?") && sp.getRemark().equals("更新备注")));
    }

    @Test
    void deleteSchedule() {
        SchedulePO po = new SchedulePO();
        po.setId(1L);
        po.setChainCode("chain-test");
        when(scheduleMapper.selectById(1L)).thenReturn(po);

        scheduleService.delete(1L);

        verify(scheduleMapper).deleteById(1L);
    }

    @Test
    void toggleStatus() {
        SchedulePO po = new SchedulePO();
        po.setId(1L);
        po.setStatus(1);
        when(scheduleMapper.selectById(1L)).thenReturn(po);

        scheduleService.toggleStatus(1L);

        assertThat(po.getStatus()).isZero(); // 1 → 0
        verify(scheduleMapper).updateById(po);
    }

    // ==================== list（含 app_code 过滤） ====================

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void list_superAdmin_noAppFilter() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        IPage<SchedulePO> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of());
        when(page.getCurrent()).thenReturn(1L);
        when(page.getSize()).thenReturn(20L);
        when(page.getTotal()).thenReturn(0L);
        when(scheduleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> page);

        IPage<ScheduleVO> result = scheduleService.list(null, null, 1, 20);

        assertThat(result).isNotNull();
        // 验证不过滤 app_code
        verify(scheduleMapper).selectPage(any(), any(LambdaQueryWrapper.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void list_normalUser_filtersByAppCode() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Set.of("app-a", "app-b"));
        IPage<SchedulePO> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of());
        when(page.getCurrent()).thenReturn(1L);
        when(page.getSize()).thenReturn(20L);
        when(page.getTotal()).thenReturn(0L);
        when(scheduleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> page);

        IPage<ScheduleVO> result = scheduleService.list(null, null, 1, 20);

        assertThat(result).isNotNull();
        verify(scheduleMapper).selectPage(any(), any(LambdaQueryWrapper.class));
    }

    // ==================== trigger ====================

    @Test
    void triggerSuccess() {
        SchedulePO schedule = new SchedulePO();
        schedule.setId(1L);
        schedule.setChainCode("chain-test");
        schedule.setRouteStrategy("round_robin");
        when(scheduleMapper.selectById(1L)).thenReturn(schedule);

        ExecutorRegistryPO executor = new ExecutorRegistryPO();
        executor.setExecutorId("e1");
        executor.setExecutorHost("192.168.1.1");
        executor.setExecutorPort(9999);
        executor.setStatus(RegistryConstants.STATUS_ONLINE);
        liveStore.touchExecutor("e1");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(executor));
        when(routeStrategy.select(anyList(), anyString())).thenReturn(executor);

        ChainExecuteResultDTO execResult = ChainExecuteResultDTO.builder()
                .instanceId("inst-1")
                .chainCode("chain-test")
                .status(3) // CHAIN_SUCCESS
                .costMs(100L)
                .build();
        when(executorClient.execute(anyString(), anyInt(), any()))
                .thenReturn(execResult);

        ScheduleLogVO logVO = scheduleService.trigger(1L);

        assertThat(logVO).isNotNull();
        assertThat(logVO.getExecutorId()).isEqualTo("e1");
        verify(scheduleLogMapper).insert(any(ScheduleLogPO.class));
    }

    @Test
    void trigger_noOnlineExecutor() {
        SchedulePO schedule = new SchedulePO();
        schedule.setId(1L);
        schedule.setChainCode("chain-test");
        when(scheduleMapper.selectById(1L)).thenReturn(schedule);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of());

        ScheduleLogVO logVO = scheduleService.trigger(1L);

        assertThat(logVO.getStatus()).isEqualTo(2); // failed
        assertThat(logVO.getErrorMessage()).contains("无可用在线执行器");
    }

    @Test
    void trigger_routeReturnsNull() {
        SchedulePO schedule = new SchedulePO();
        schedule.setId(1L);
        schedule.setChainCode("chain-test");
        schedule.setRouteStrategy("round_robin");
        when(scheduleMapper.selectById(1L)).thenReturn(schedule);

        ExecutorRegistryPO executor = new ExecutorRegistryPO();
        executor.setExecutorId("e1");
        executor.setExecutorHost("192.168.1.1");
        executor.setExecutorPort(9999);
        executor.setStatus(RegistryConstants.STATUS_ONLINE);
        liveStore.touchExecutor("e1");
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(executor));
        when(routeStrategy.select(anyList(), anyString())).thenReturn(null);

        ScheduleLogVO logVO = scheduleService.trigger(1L);

        assertThat(logVO.getStatus()).isEqualTo(2);
        assertThat(logVO.getErrorMessage()).contains("路由策略未选中执行器");
    }

    // ==================== listLogs ====================

    @Test
    void listLogs_withScheduleId() {
        when(scheduleLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<ScheduleLogVO> page = new Page<>(1, 20);
                    page.setRecords(Collections.emptyList());
                    return page;
                });

        IPage<ScheduleLogVO> result = scheduleService.listLogs(1L, null, 1, 20);

        assertThat(result).isNotNull();
    }

    @Test
    void listLogs_withStatusFilter() {
        when(scheduleLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> new Page<>(1, 20));

        IPage<ScheduleLogVO> result = scheduleService.listLogs(null, 2, 1, 20);

        assertThat(result).isNotNull();
    }
}
