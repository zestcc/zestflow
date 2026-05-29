package com.zestflow.admin.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import com.zestflow.admin.service.impl.ScheduleServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 调度监控器 — 定期扫描启用的调度，检查 cron 是否需要触发
 * <p>
 * 每 15 秒扫描一次，对标 xxl-job 的调度轮询机制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleMonitor {

    private final ScheduleMapper scheduleMapper;
    private final ScheduleLogMapper scheduleLogMapper;
    private final ScheduleServiceImpl scheduleService;

    @Scheduled(fixedDelay = 15_000)
    public void scan() {
        List<SchedulePO> enabledSchedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<SchedulePO>()
                        .eq(SchedulePO::getStatus, 1)
        );

        if (enabledSchedules.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (SchedulePO schedule : enabledSchedules) {
            try {
                org.springframework.scheduling.support.CronExpression cronExpression =
                        org.springframework.scheduling.support.CronExpression.parse(schedule.getCron());

                LocalDateTime lastTrigger = getLastTriggerTime(schedule.getId());

                ZonedDateTime afterDate = lastTrigger != null
                        ? lastTrigger.atZone(ZoneId.systemDefault())
                        : now.minusMinutes(5).atZone(ZoneId.systemDefault());

                ZonedDateTime next = cronExpression.next(afterDate);
                if (next == null) continue;

                LocalDateTime nextTime = next.toLocalDateTime();

                if (!nextTime.isAfter(now)) {
                    log.info("调度触发 scheduleId={} chainCode={} cron={}",
                            schedule.getId(), schedule.getChainCode(), schedule.getCron());
                    scheduleService.doTrigger(schedule, "cron");
                }
            } catch (Exception e) {
                log.error("调度扫描异常 scheduleId={}", schedule.getId(), e);
            }
        }
    }

    private LocalDateTime getLastTriggerTime(Long scheduleId) {
        ScheduleLogPO lastLog = scheduleLogMapper.selectOne(
                new LambdaQueryWrapper<ScheduleLogPO>()
                        .eq(ScheduleLogPO::getScheduleId, scheduleId)
                        .orderByDesc(ScheduleLogPO::getTriggeredAt)
                        .last("LIMIT 1")
        );
        return lastLog != null ? lastLog.getTriggeredAt() : null;
    }
}
