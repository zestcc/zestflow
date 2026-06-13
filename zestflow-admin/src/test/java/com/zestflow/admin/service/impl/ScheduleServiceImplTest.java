package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.ScheduleCreateDTO;
import com.zestflow.admin.model.dto.ScheduleUpdateDTO;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.model.vo.ScheduleLogVO;
import com.zestflow.admin.model.vo.ScheduleVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import com.zestflow.admin.schedule.ScheduleChainProxyService;
import com.zestflow.admin.schedule.platform.PlatformJobRunner;
import com.zestflow.admin.schedule.platform.ScheduleJobType;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @Mock private ScheduleMapper scheduleMapper;
    @Mock private ScheduleLogMapper scheduleLogMapper;
    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Mock private PlatformJobRunner platformJobRunner;
    @Mock private TenantAppContext tenantAppContext;
    @Mock private ScheduleChainProxyService scheduleChainProxyService;

    private ScheduleServiceImpl scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleServiceImpl(
                scheduleMapper, scheduleLogMapper, executorRegistryMapper, tenantAppContext,
                platformJobRunner, scheduleChainProxyService);
        lenient().when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Set.of("app-a"));
    }

    @Test
    void createChainSchedule_delegatesToProxy() {
        ScheduleCreateDTO dto = new ScheduleCreateDTO();
        dto.setChainCode("chain-test");
        dto.setChainName("测试链");
        dto.setCron("0 */5 * * * ?");

        ScheduleVO expected = ScheduleVO.builder().id(1L).chainCode("chain-test").cron(dto.getCron()).build();
        when(scheduleChainProxyService.create(eq("app-a"), eq(dto), eq("admin"))).thenReturn(expected);

        ScheduleVO vo = scheduleService.create(dto, "admin");

        assertThat(vo.getChainCode()).isEqualTo("chain-test");
        verify(scheduleChainProxyService).create("app-a", dto, "admin");
    }

    @Test
    void listChain_delegatesToProxy() {
        Page<ScheduleVO> page = new Page<>(1, 20, 0);
        when(scheduleChainProxyService.list(eq("app-a"), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        IPage<ScheduleVO> result = scheduleService.list(null, ScheduleJobType.CHAIN, null, 1, 20);

        assertThat(result).isSameAs(page);
    }

    @Test
    void getPlatformSchedule_fromAdminDb() {
        SchedulePO po = new SchedulePO();
        po.setId(1L);
        po.setJobType(ScheduleJobType.PLATFORM);
        po.setChainCode("admin.tenant.cleanup");
        po.setCron("每 5 分钟");
        po.setStatus(1);
        when(scheduleMapper.selectById(1L)).thenReturn(po);

        ScheduleVO vo = scheduleService.getById(1L);

        assertThat(vo.getJobType()).isEqualTo(ScheduleJobType.PLATFORM);
        verify(scheduleChainProxyService, never()).getById(anyString(), anyLong());
    }

    @Test
    void getChainSchedule_delegatesToProxy() {
        when(scheduleMapper.selectById(2L)).thenReturn(null);
        ScheduleVO expected = ScheduleVO.builder().id(2L).chainCode("c1").jobType(ScheduleJobType.CHAIN).build();
        when(scheduleChainProxyService.getById("app-a", 2L)).thenReturn(expected);

        ScheduleVO vo = scheduleService.getById(2L);

        assertThat(vo.getChainCode()).isEqualTo("c1");
    }

    @Test
    void triggerChain_delegatesToProxy() {
        when(scheduleMapper.selectById(3L)).thenReturn(null);
        ScheduleLogVO log = ScheduleLogVO.builder().id(10L).scheduleId(3L).status(1).build();
        when(scheduleChainProxyService.trigger("app-a", 3L)).thenReturn(log);

        ScheduleLogVO result = scheduleService.trigger(3L);

        assertThat(result.getStatus()).isEqualTo(1);
    }

    @Test
    void create_withoutAppCode_throws() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        ScheduleCreateDTO dto = new ScheduleCreateDTO();
        dto.setChainCode("x");
        dto.setChainName("x");
        dto.setCron("* * * * * ?");

        assertThatThrownBy(() -> scheduleService.create(dto, "admin"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    void listPlatform_fromAdminDb() {
        Page<SchedulePO> poPage = new Page<>(1, 20, 0);
        when(scheduleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(poPage);

        scheduleService.list(null, ScheduleJobType.PLATFORM, null, 1, 20);

        verify(scheduleMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }
}
