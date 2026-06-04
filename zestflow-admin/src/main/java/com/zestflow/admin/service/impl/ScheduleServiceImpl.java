package com.zestflow.admin.service.impl;

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
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.registry.RegistryOnlineQuerySupport;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import com.zestflow.admin.schedule.ScheduleIdempotencyKeys;
import com.zestflow.admin.schedule.ExecutorClient;
import com.zestflow.admin.schedule.RouteStrategy;
import com.zestflow.admin.schedule.ScheduleExecutorFailover;
import com.zestflow.admin.service.ScheduleService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final ScheduleLogMapper scheduleLogMapper;
    private final ExecutorRegistryMapper executorRegistryMapper;
    private final RegistryLiveStore liveStore;
    private final ExecutorClient executorClient;
    private final TenantAppContext tenantAppContext;
    private final List<RouteStrategy> routeStrategies;

    @Override
    public IPage<ScheduleVO> list(String keyword, Integer status, Integer page, Integer size) {
        LambdaQueryWrapper<SchedulePO> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SchedulePO::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SchedulePO::getChainCode, keyword)
                    .or().like(SchedulePO::getChainName, keyword));
        }
        // 非超管按 appCode 过滤
        Set<String> accessibleCodes = tenantAppContext.getCurrentUserAppCodes();
        if (accessibleCodes != null && !accessibleCodes.isEmpty()) {
            wrapper.in(SchedulePO::getAppCode, accessibleCodes);
        }
        wrapper.orderByDesc(SchedulePO::getCreatedAt);

        IPage<SchedulePO> poPage = scheduleMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ScheduleVO> voPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        voPage.setRecords(poPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ScheduleVO getById(Long id) {
        SchedulePO po = scheduleMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        return toVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO create(ScheduleCreateDTO dto, String username) {
        SchedulePO po = new SchedulePO();
        po.setChainCode(dto.getChainCode());
        po.setChainName(dto.getChainName());
        po.setCron(dto.getCron());
        po.setRouteStrategy(dto.getRouteStrategy() != null ? dto.getRouteStrategy() : "round_robin");
        po.setParams(dto.getParams());
        po.setStatus(1);
        po.setRemark(dto.getRemark());
        po.setCreatedBy(username);
        po.setTenantId(tenantAppContext.getCurrentTenantId());
        // 自动关联用户首个可访问应用（若无显式 appCode）
        Set<String> accessibleCodes = tenantAppContext.getCurrentUserAppCodes();
        if (accessibleCodes != null && !accessibleCodes.isEmpty()) {
            po.setAppCode(accessibleCodes.iterator().next());
        }

        scheduleMapper.insert(po);

        log.info("调度创建成功 scheduleId={} chainCode={} cron={}", po.getId(), dto.getChainCode(), dto.getCron());
        return toVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO update(Long id, ScheduleUpdateDTO dto) {
        SchedulePO po = scheduleMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        if (dto.getCron() != null) po.setCron(dto.getCron());
        if (dto.getRouteStrategy() != null) po.setRouteStrategy(dto.getRouteStrategy());
        if (dto.getParams() != null) po.setParams(dto.getParams());
        if (dto.getRemark() != null) po.setRemark(dto.getRemark());
        if (dto.getStatus() != null) po.setStatus(dto.getStatus());

        scheduleMapper.updateById(po);
        log.info("调度更新成功 scheduleId={}", id);
        return toVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SchedulePO po = scheduleMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        scheduleMapper.deleteById(id);
        log.info("调度删除成功 scheduleId={} chainCode={}", id, po.getChainCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        SchedulePO po = scheduleMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        int newStatus = po.getStatus() == 1 ? 0 : 1;
        po.setStatus(newStatus);

        scheduleMapper.updateById(po);
        log.info("调度状态切换 scheduleId={} newStatus={}", id, newStatus);
    }

    @Override
    public ScheduleLogVO trigger(Long id) {
        SchedulePO schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        return doTrigger(schedule, "manual");
    }

    @Override
    public IPage<ScheduleLogVO> listLogs(Long scheduleId, Integer status, Integer page, Integer size) {
        LambdaQueryWrapper<ScheduleLogPO> wrapper = new LambdaQueryWrapper<ScheduleLogPO>()
                .eq(scheduleId != null, ScheduleLogPO::getScheduleId, scheduleId)
                .eq(status != null, ScheduleLogPO::getStatus, status)
                .orderByDesc(ScheduleLogPO::getTriggeredAt);

        IPage<ScheduleLogPO> poPage = scheduleLogMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ScheduleLogVO> voPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        voPage.setRecords(poPage.getRecords().stream().map(this::toLogVO).collect(Collectors.toList()));
        return voPage;
    }

    public ScheduleLogVO doTrigger(SchedulePO schedule, String triggerType) {
        String idempotencyKey = "manual".equals(triggerType)
                ? ScheduleIdempotencyKeys.forManualTrigger(schedule.getId())
                : null;
        return doTrigger(schedule, triggerType, idempotencyKey);
    }

    /**
     * 执行一次调度触发（供调度扫描与 trigger() 调用）
     *
     * @param idempotencyKey 幂等键；cron 扫描应传入 {@link ScheduleIdempotencyKeys#forCronFire}
     */
    public ScheduleLogVO doTrigger(SchedulePO schedule, String triggerType, String idempotencyKey) {
        long startTime = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        // 1. 查找当前应用下的在线执行器
        List<ExecutorRegistryPO> onlineExecutors = findOnlineExecutors(schedule.getAppCode());

        ScheduleLogPO logPo = new ScheduleLogPO();
        logPo.setScheduleId(schedule.getId());
        logPo.setChainCode(schedule.getChainCode());
        logPo.setTriggerType(triggerType);
        logPo.setParams(schedule.getParams());
        logPo.setTriggeredAt(now);
        logPo.setStatus(0); // 运行中

        if (onlineExecutors.isEmpty()) {
            logPo.setStatus(2);
            logPo.setErrorMessage("无可用在线执行器");
            logPo.setCostMs(System.currentTimeMillis() - startTime);
            scheduleLogMapper.insert(logPo);
            log.warn("调度触发失败，无在线执行器 scheduleId={}", schedule.getId());
            return toLogVO(logPo);
        }

        // 2. 路由 + failover 执行（对标 xxl-job 失败切换）
        RouteStrategy strategy = findStrategy(schedule.getRouteStrategy());
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(schedule.getParams());
        ScheduleExecutorFailover.FailoverResult failover = ScheduleExecutorFailover.executeWithFailover(
                onlineExecutors, strategy, schedule.getChainCode(), params, idempotencyKey, executorClient);

        ExecutorRegistryPO target = failover.getExecutor();
        ChainExecuteResultDTO result = failover.getResult();
        if (target == null) {
            logPo.setStatus(2);
            logPo.setErrorMessage(result != null ? result.getErrorMessage() : "路由失败");
            logPo.setCostMs(System.currentTimeMillis() - startTime);
            scheduleLogMapper.insert(logPo);
            return toLogVO(logPo);
        }

        logPo.setExecutorId(target.getExecutorId());
        logPo.setExecutorAddress(target.getExecutorHost() + ":" + target.getExecutorPort());
        logPo.setRouteStrategy(strategy.name());

        // 3. 记录结果
        long costMs = System.currentTimeMillis() - startTime;
        logPo.setCostMs(costMs);
        if (ScheduleExecutorFailover.isSuccess(result)) {
            logPo.setStatus(1); // 成功
        } else {
            logPo.setStatus(2); // 失败
            if (failover.getAttempted() > 1) {
                log.warn("调度 failover 全部失败 scheduleId={} attempted={} lastError={}",
                        schedule.getId(), failover.getAttempted(), result.getErrorMessage());
            }
        }
        logPo.setErrorMessage(result.getErrorMessage());
        scheduleLogMapper.insert(logPo);

        log.info("调度触发完成 scheduleId={} chainCode={} executor={}:{} status={} cost={}ms attempted={}",
                schedule.getId(), schedule.getChainCode(),
                target.getExecutorHost(), target.getExecutorPort(),
                logPo.getStatus(), costMs, failover.getAttempted());

        return toLogVO(logPo);
    }

    List<ExecutorRegistryPO> findOnlineExecutors(String appCode) {
        return RegistryOnlineQuerySupport.listLiveOnlineExecutors(executorRegistryMapper, liveStore, appCode);
    }

    private RouteStrategy findStrategy(String name) {
        for (RouteStrategy s : routeStrategies) {
            if (s.name().equals(name)) return s;
        }
        return routeStrategies.get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String params) {
        if (params == null || params.isBlank()) return Collections.emptyMap();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(params, Map.class);
        } catch (Exception e) {
            log.warn("解析调度参数失败，使用空参数", e);
            return Collections.emptyMap();
        }
    }

    private ScheduleVO toVO(SchedulePO po) {
        return ScheduleVO.builder()
                .id(po.getId())
                .chainId(po.getChainId())
                .chainCode(po.getChainCode())
                .chainName(po.getChainName())
                .cron(po.getCron())
                .routeStrategy(po.getRouteStrategy())
                .params(po.getParams())
                .status(po.getStatus())
                .remark(po.getRemark())
                .createdBy(po.getCreatedBy())
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private ScheduleLogVO toLogVO(ScheduleLogPO po) {
        return ScheduleLogVO.builder()
                .id(po.getId())
                .scheduleId(po.getScheduleId())
                .chainCode(po.getChainCode())
                .executorId(po.getExecutorId())
                .executorAddress(po.getExecutorAddress())
                .routeStrategy(po.getRouteStrategy())
                .triggerType(po.getTriggerType())
                .params(po.getParams())
                .status(po.getStatus())
                .resultData(po.getResultData())
                .errorMessage(po.getErrorMessage())
                .costMs(po.getCostMs())
                .triggeredAt(po.getTriggeredAt())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
