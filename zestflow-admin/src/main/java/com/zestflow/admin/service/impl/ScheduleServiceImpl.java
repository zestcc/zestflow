package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.constant.ErrorCode;

import com.zestflow.admin.model.dto.ScheduleCreateDTO;
import com.zestflow.admin.model.dto.ScheduleUpdateDTO;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.vo.ScheduleLogStatsVO;
import com.zestflow.admin.model.vo.ScheduleLogVO;
import com.zestflow.admin.model.vo.ScheduleVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import com.zestflow.admin.schedule.ScheduleChainProxyService;
import com.zestflow.admin.schedule.platform.PlatformJobRunner;
import com.zestflow.admin.schedule.platform.ScheduleJobType;
import com.zestflow.admin.service.ScheduleService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final ScheduleLogMapper scheduleLogMapper;
    private final ExecutorRegistryMapper executorRegistryMapper;
    private final TenantAppContext tenantAppContext;
    private final PlatformJobRunner platformJobRunner;
    private final ScheduleChainProxyService scheduleChainProxyService;

    @Override
    public IPage<ScheduleVO> list(String keyword, String jobType, Integer status, Integer page, Integer size) {
        if (ScheduleJobType.CHAIN.equals(jobType)) {
            return scheduleChainProxyService.list(resolvePrimaryAppCode(), keyword, status, page, size);
        }
        LambdaQueryWrapper<SchedulePO> wrapper = new LambdaQueryWrapper<>();
        if (jobType != null && !jobType.isBlank()) {
            wrapper.eq(SchedulePO::getJobType, jobType);
        }
        if (status != null) {
            wrapper.eq(SchedulePO::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SchedulePO::getChainCode, keyword)
                    .or().like(SchedulePO::getChainName, keyword)
                    .or().like(SchedulePO::getJobKey, keyword));
        }
        // 非超管按 appCode 过滤（平台任务无 appCode，始终可见）
        Set<String> accessibleCodes = tenantAppContext.getCurrentUserAppCodes();
        if (accessibleCodes != null && !accessibleCodes.isEmpty()) {
            wrapper.and(w -> w.isNull(SchedulePO::getAppCode)
                    .or().in(SchedulePO::getAppCode, accessibleCodes)
                    .or().eq(SchedulePO::getJobType, ScheduleJobType.PLATFORM));
        }
        wrapper.orderByDesc(SchedulePO::getCreatedAt);

        IPage<SchedulePO> poPage = scheduleMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ScheduleVO> voPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        voPage.setRecords(poPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ScheduleVO getById(Long id) {
        SchedulePO platform = scheduleMapper.selectById(id);
        if (platform != null && ScheduleJobType.PLATFORM.equals(platform.getJobType())) {
            return toVO(platform);
        }
        return scheduleChainProxyService.getById(resolveAppCodeForChainSchedule(id), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO create(ScheduleCreateDTO dto, String username) {
        return scheduleChainProxyService.create(resolvePrimaryAppCode(), dto, username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO update(Long id, ScheduleUpdateDTO dto) {
        SchedulePO po = scheduleMapper.selectById(id);
        if (po != null && ScheduleJobType.PLATFORM.equals(po.getJobType())) {
            if (po.getEditable() != null && po.getEditable() == 0) {
                throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, "平台内置任务不可编辑");
            }
            if (dto.getCron() != null) {
                po.setCron(dto.getCron());
            }
            if (dto.getRouteStrategy() != null) {
                po.setRouteStrategy(dto.getRouteStrategy());
            }
            if (dto.getParams() != null) {
                po.setParams(dto.getParams());
            }
            if (dto.getRemark() != null) {
                po.setRemark(dto.getRemark());
            }
            if (dto.getStatus() != null) {
                po.setStatus(dto.getStatus());
            }
            scheduleMapper.updateById(po);
            log.info("调度更新成功 scheduleId={}", id);
            return toVO(po);
        }
        return scheduleChainProxyService.update(resolveAppCodeForChainSchedule(id), id, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SchedulePO po = scheduleMapper.selectById(id);
        if (po != null && ScheduleJobType.PLATFORM.equals(po.getJobType())) {
            if (po.getEditable() != null && po.getEditable() == 0) {
                throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, "平台内置任务不可删除");
            }
            scheduleMapper.deleteById(id);
            log.info("调度删除成功 scheduleId={} chainCode={}", id, po.getChainCode());
            return;
        }
        scheduleChainProxyService.delete(resolveAppCodeForChainSchedule(id), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        SchedulePO po = scheduleMapper.selectById(id);
        if (po != null && ScheduleJobType.PLATFORM.equals(po.getJobType())) {
            int newStatus = po.getStatus() == 1 ? 0 : 1;
            po.setStatus(newStatus);
            scheduleMapper.updateById(po);
            log.info("调度状态切换 scheduleId={} newStatus={}", id, newStatus);
            return;
        }
        scheduleChainProxyService.toggleStatus(resolveAppCodeForChainSchedule(id), id);
    }

    @Override
    public ScheduleLogVO trigger(Long id) {
        SchedulePO schedule = scheduleMapper.selectById(id);
        if (schedule != null && ScheduleJobType.PLATFORM.equals(schedule.getJobType())) {
            if (schedule.getRemote() != null && schedule.getRemote() == 1) {
                throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, "节点本地任务请在对应节点查看执行状态");
            }
            try {
                return platformJobRunner.runManual(schedule.getJobKey());
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, e.getMessage());
            }
        }
        return scheduleChainProxyService.trigger(resolveAppCodeForChainSchedule(id), id);
    }

    @Override
    public IPage<ScheduleLogVO> listLogs(Long scheduleId, String jobType, String keyword, Integer status, Integer page, Integer size) {
        if (ScheduleJobType.CHAIN.equals(jobType)) {
            return scheduleChainProxyService.listLogs(resolvePrimaryAppCode(), scheduleId, keyword, status, page, size);
        }
        if (scheduleId != null) {
            SchedulePO po = scheduleMapper.selectById(scheduleId);
            if (po == null || !ScheduleJobType.PLATFORM.equals(po.getJobType())) {
                return scheduleChainProxyService.listLogs(resolveAppCodeForChainSchedule(scheduleId),
                        scheduleId, keyword, status, page, size);
            }
        }
        LambdaQueryWrapper<ScheduleLogPO> wrapper = new LambdaQueryWrapper<ScheduleLogPO>()
                .eq(scheduleId != null, ScheduleLogPO::getScheduleId, scheduleId)
                .eq(status != null, ScheduleLogPO::getStatus, status)
                .orderByDesc(ScheduleLogPO::getTriggeredAt);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ScheduleLogPO::getChainCode, keyword)
                    .or().like(ScheduleLogPO::getJobKey, keyword)
                    .or().like(ScheduleLogPO::getJobName, keyword));
        }
        if (jobType != null && !jobType.isBlank()) {
            List<Long> scheduleIds = scheduleMapper.selectList(
                            new LambdaQueryWrapper<SchedulePO>()
                                    .eq(SchedulePO::getJobType, jobType)
                                    .select(SchedulePO::getId))
                    .stream()
                    .map(SchedulePO::getId)
                    .toList();
            if (scheduleIds.isEmpty()) {
                Page<ScheduleLogVO> empty = new Page<>(page, size, 0);
                empty.setRecords(List.of());
                return empty;
            }
            wrapper.in(ScheduleLogPO::getScheduleId, scheduleIds);
        }

        IPage<ScheduleLogPO> poPage = scheduleLogMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ScheduleLogVO> voPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        voPage.setRecords(poPage.getRecords().stream().map(this::toLogVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ScheduleLogStatsVO getLogStats(Integer hours) {
        int windowHours = hours != null && hours > 0 ? hours : 24;
        ScheduleLogStatsVO chainStats = scheduleChainProxyService.logStats(resolvePrimaryAppCode(), windowHours);

        LocalDateTime since = LocalDateTime.now().minusHours(windowHours);
        List<ScheduleLogPO> platformLogs = scheduleLogMapper.selectList(
                new LambdaQueryWrapper<ScheduleLogPO>()
                        .ge(ScheduleLogPO::getTriggeredAt, since)
                        .isNotNull(ScheduleLogPO::getJobKey));

        long pTotal = platformLogs.size();
        long pSuccess = platformLogs.stream().filter(l -> l.getStatus() != null && l.getStatus() == 1).count();
        long pFailed = platformLogs.stream().filter(l -> l.getStatus() != null && l.getStatus() == 2).count();
        long pRunning = platformLogs.stream().filter(l -> l.getStatus() != null && l.getStatus() == 0).count();

        long total = chainStats.getTotalCount() + pTotal;
        long success = chainStats.getSuccessCount() + pSuccess;
        long failed = chainStats.getFailedCount() + pFailed;
        long running = chainStats.getRunningCount() + pRunning;
        double rate = (success + failed) > 0 ? (double) success / (success + failed) * 100.0 : 0.0;

        return ScheduleLogStatsVO.builder()
                .totalCount(total)
                .successCount(success)
                .failedCount(failed)
                .runningCount(running)
                .successRate(Math.round(rate * 10) / 10.0)
                .avgCostMs(chainStats.getAvgCostMs())
                .build();
    }

    private String resolvePrimaryAppCode() {
        Set<String> codes = tenantAppContext.getCurrentUserAppCodes();
        if (codes != null && !codes.isEmpty()) {
            return codes.iterator().next();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && SecurityUtils.isSuperAdmin(auth)) {
            String registered = resolveFirstRegisteredAppCode();
            if (registered != null) {
                return registered;
            }
        }
        throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, "无可用应用模块");
    }

    /** 超管无 user_app_role 时，从执行器注册表取首个 appCode */
    private String resolveFirstRegisteredAppCode() {
        List<ExecutorRegistryPO> rows = executorRegistryMapper.selectList(
                new QueryWrapper<ExecutorRegistryPO>()
                        .select("DISTINCT app_code")
                        .isNotNull("app_code")
                        .ne("app_code", "")
                        .orderByAsc("app_code")
                        .last("LIMIT 1"));
        if (rows.isEmpty()) {
            return null;
        }
        String appCode = rows.get(0).getAppCode();
        return (appCode == null || appCode.isBlank()) ? null : appCode;
    }

    private String resolveAppCodeForChainSchedule(Long scheduleId) {
        String primary = resolvePrimaryAppCode();
        try {
            scheduleChainProxyService.getById(primary, scheduleId);
            return primary;
        } catch (BizException e) {
            Set<String> codes = tenantAppContext.getCurrentUserAppCodes();
            if (codes != null) {
                for (String code : codes) {
                    if (code.equals(primary)) {
                        continue;
                    }
                    try {
                        scheduleChainProxyService.getById(code, scheduleId);
                        return code;
                    } catch (BizException ignored) {
                        // try next app
                    }
                }
            }
            throw e;
        }
    }

    private ScheduleVO toVO(SchedulePO po) {
        return ScheduleVO.builder()
                .id(po.getId())
                .chainId(po.getChainId())
                .chainCode(po.getChainCode())
                .chainName(po.getChainName())
                .jobType(po.getJobType() != null ? po.getJobType() : ScheduleJobType.CHAIN)
                .jobKey(po.getJobKey())
                .scheduleKind(po.getScheduleKind())
                .fixedIntervalMs(po.getFixedIntervalMs())
                .module(po.getModule())
                .editable(po.getEditable() == null || po.getEditable() == 1)
                .remote(po.getRemote() != null && po.getRemote() == 1)
                .lastTriggerAt(po.getLastTriggerAt())
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
                .jobKey(po.getJobKey())
                .jobName(po.getJobName())
                .chainCode(po.getChainCode())
                .executorId(po.getExecutorId())
                .executorAddress(po.getExecutorAddress())
                .executionId(po.getExecutionId())
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
