package com.zestflow.executor.schedule;

import com.zestflow.common.spi.schedule.ScheduleDriver;
import com.zestflow.common.spi.schedule.ScheduleDriverIds;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 嵌入式调度驱动 — 读业务库 zf_schedule，本地 Cron + 分片触发。
 */
@Slf4j
@RequiredArgsConstructor
public class EmbeddedScheduleDriver implements ScheduleDriver {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleTriggerService triggerService;
    private final ExecutorProperties executorProperties;
    private final ExecutorScheduleProperties scheduleProperties;

    @Override
    public String driverId() {
        return ScheduleDriverIds.EMBEDDED;
    }

    @Override
    public void start() {
        log.info("EmbeddedScheduleDriver 已启用 pollIntervalMs={} shardIndex={}/{}",
                scheduleProperties.getPollIntervalMs(),
                executorProperties.getShardIndex(),
                executorProperties.getShardTotal());
    }

    @Override
    public void stop() {
        log.info("EmbeddedScheduleDriver 已停止");
    }

    @Scheduled(fixedDelayString = "${zestflow.executor.schedule.poll-interval-ms:15000}")
    public void scanAndTriggerDueSchedules() {
        if (!scheduleProperties.isEnabled()) {
            return;
        }
        List<SchedulePO> schedules = scheduleRepository.listEnabled();
        if (schedules.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int shardIndex = executorProperties.getShardIndex();
        int executorShardTotal = executorProperties.getShardTotal();

        for (SchedulePO schedule : schedules) {
            if (!shouldOwn(schedule, shardIndex, executorShardTotal)) {
                continue;
            }
            try {
                CronExpression cron = CronExpression.parse(schedule.getCron());
                LocalDateTime lastTrigger = scheduleRepository.lastTriggerTime(schedule.getId()).orElse(null);
                ZonedDateTime afterDate = lastTrigger != null
                        ? lastTrigger.atZone(ZoneId.systemDefault())
                        : now.minusMinutes(5).atZone(ZoneId.systemDefault());
                ZonedDateTime next = cron.next(afterDate);
                if (next == null) {
                    continue;
                }
                LocalDateTime nextTime = next.toLocalDateTime();
                if (!nextTime.isAfter(now)) {
                    String idempotencyKey = ScheduleIdempotencyKeys.forCronFire(schedule.getId(), nextTime);
                    triggerService.trigger(schedule, "cron", idempotencyKey);
                }
            } catch (Exception e) {
                log.error("调度扫描异常 scheduleId={}", schedule.getId(), e);
            }
        }
    }

    static boolean shouldOwn(SchedulePO schedule, int shardIndex, int executorShardTotal) {
        int total = ScheduleShardSupport.effectiveShardTotal(schedule.getShardTotal(), executorShardTotal);
        return ScheduleShardSupport.ownsSchedule(schedule.getId(), total, shardIndex);
    }
}
