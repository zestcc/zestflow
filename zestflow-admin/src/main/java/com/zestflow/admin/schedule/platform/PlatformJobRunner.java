package com.zestflow.admin.schedule.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.model.vo.ScheduleLogVO;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 平台任务统一执行器 — 包装所有 Admin 内置 @Scheduled，写入 schedule_log。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformJobRunner {

    private final ScheduleMapper scheduleMapper;
    private final ScheduleLogMapper scheduleLogMapper;
    private final PlatformJobHandlerRegistry handlerRegistry;

    /** 按 jobKey 从注册表执行（供 @Scheduled 入口调用） */
    public void runScheduledByKey(String jobKey) {
        PlatformJobHandler handler = handlerRegistry.get(jobKey);
        if (handler == null) {
            log.warn("平台任务 handler 未注册 jobKey={}", jobKey);
            return;
        }
        runInternal(jobKey, "cron", handler);
    }

    public void runScheduled(String jobKey, Runnable action) {
        runScheduled(jobKey, () -> {
            action.run();
            return null;
        });
    }

    public void runScheduled(String jobKey, PlatformJobHandler handler) {
        runInternal(jobKey, "cron", handler);
    }

    public ScheduleLogVO runManual(String jobKey) {
        SchedulePO job = requireRunnableJob(jobKey);
        if (job.getRemote() != null && job.getRemote() == 1) {
            throw new IllegalStateException("节点本地任务无法在 Admin 侧手动触发");
        }
        PlatformJobHandler handler = handlerRegistry.get(jobKey);
        if (handler == null) {
            throw new IllegalStateException("平台任务未注册执行器: " + jobKey);
        }
        ScheduleLogPO logPo = runInternal(jobKey, "manual", handler);
        return logPo != null ? toLogVo(logPo) : null;
    }

    private ScheduleLogPO runInternal(String jobKey, String triggerType, PlatformJobHandler handler) {
        SchedulePO job = findByJobKey(jobKey);
        if (job == null) {
            log.warn("平台任务未登记 jobKey={}，跳过执行", jobKey);
            return null;
        }
        if (job.getStatus() == null || job.getStatus() != 1) {
            log.debug("平台任务已停用 jobKey={}", jobKey);
            return null;
        }
        if (job.getRemote() != null && job.getRemote() == 1) {
            log.debug("远程节点任务跳过 Admin 执行 jobKey={}", jobKey);
            return null;
        }

        long start = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        ScheduleLogPO logPo = new ScheduleLogPO();
        logPo.setScheduleId(job.getId());
        logPo.setJobKey(jobKey);
        logPo.setJobName(job.getChainName());
        logPo.setChainCode(jobKey);
        logPo.setTriggerType(triggerType);
        logPo.setStatus(0);
        logPo.setTriggeredAt(now);
        logPo.setTenantId(job.getTenantId());
        scheduleLogMapper.insert(logPo);

        try {
            String summary = handler.execute();
            logPo.setStatus(1);
            if (summary != null && !summary.isBlank()) {
                logPo.setResultData(summary);
            }
        } catch (Exception e) {
            logPo.setStatus(2);
            logPo.setErrorMessage(truncate(e.getMessage(), 2000));
            log.error("平台任务执行失败 jobKey={}", jobKey, e);
        } finally {
            logPo.setCostMs(System.currentTimeMillis() - start);
            scheduleLogMapper.updateById(logPo);
            job.setLastTriggerAt(now);
            scheduleMapper.updateById(job);
        }
        return logPo;
    }

    private SchedulePO requireRunnableJob(String jobKey) {
        SchedulePO job = findByJobKey(jobKey);
        if (job == null) {
            throw new IllegalArgumentException("平台任务不存在: " + jobKey);
        }
        return job;
    }

    private SchedulePO findByJobKey(String jobKey) {
        return scheduleMapper.selectOne(new LambdaQueryWrapper<SchedulePO>()
                .eq(SchedulePO::getJobKey, jobKey)
                .last("LIMIT 1"));
    }

    private static ScheduleLogVO toLogVo(ScheduleLogPO po) {
        return ScheduleLogVO.builder()
                .id(po.getId())
                .scheduleId(po.getScheduleId())
                .jobKey(po.getJobKey())
                .jobName(po.getJobName())
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

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
