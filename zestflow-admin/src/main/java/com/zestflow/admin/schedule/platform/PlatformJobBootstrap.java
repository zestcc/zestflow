package com.zestflow.admin.schedule.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.repository.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时将 {@link PlatformJobCatalog} 同步到 schedule 表，保证调度中心可见全部平台任务。
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class PlatformJobBootstrap implements ApplicationRunner {

    private final ScheduleMapper scheduleMapper;

    @Override
    public void run(ApplicationArguments args) {
        for (PlatformJobDefinition def : PlatformJobCatalog.all()) {
            upsert(def);
        }
        log.info("平台内置任务已同步至调度中心 count={}", PlatformJobCatalog.all().size());
    }

    private void upsert(PlatformJobDefinition def) {
        SchedulePO existing = scheduleMapper.selectOne(new LambdaQueryWrapper<SchedulePO>()
                .eq(SchedulePO::getJobKey, def.getJobKey())
                .last("LIMIT 1"));

        if (existing == null) {
            SchedulePO po = new SchedulePO();
            fillFromDefinition(po, def);
            po.setStatus(1);
            po.setCreatedBy("system");
            po.setTenantId(1L);
            scheduleMapper.insert(po);
            return;
        }

        Integer preservedStatus = existing.getStatus();
        fillFromDefinition(existing, def);
        existing.setStatus(preservedStatus != null ? preservedStatus : 1);
        scheduleMapper.updateById(existing);
    }

    private void fillFromDefinition(SchedulePO po, PlatformJobDefinition def) {
        po.setJobType(ScheduleJobType.PLATFORM);
        po.setJobKey(def.getJobKey());
        po.setChainCode(def.getJobKey());
        po.setChainName(def.getName());
        po.setChainId(null);
        po.setScheduleKind(def.getScheduleKind());
        po.setFixedIntervalMs(def.getFixedIntervalMs());
        po.setCron(formatScheduleDisplay(def));
        po.setModule(def.getModule());
        po.setEditable(def.isEditable() ? 1 : 0);
        po.setRemote(def.isRemote() ? 1 : 0);
        po.setRemark(def.getRemark());
        po.setRouteStrategy(null);
        po.setParams(null);
        po.setUpdatedBy("system");
    }

    static String formatScheduleDisplay(PlatformJobDefinition def) {
        if (ScheduleKind.CRON.equals(def.getScheduleKind()) && def.getCron() != null) {
            return def.getCron();
        }
        long ms = def.getFixedIntervalMs() != null ? def.getFixedIntervalMs() : 0;
        if (ms <= 0) {
            return "-";
        }
        if (ms % 60_000 == 0) {
            return "每 " + (ms / 60_000) + " 分钟";
        }
        if (ms % 1000 == 0) {
            return "每 " + (ms / 1000) + " 秒";
        }
        return "每 " + ms + " ms";
    }
}
