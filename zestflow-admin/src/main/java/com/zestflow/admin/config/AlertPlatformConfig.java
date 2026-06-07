package com.zestflow.admin.config;

import com.zestflow.admin.alert.AlertProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AlertPlatformConfig {

    private final PlatformConfigReader platformConfig;
    private final AlertProperties yaml;

    public boolean isEnabled() {
        return platformConfig.getBoolean(SysConfigKeys.ALERT_ENABLED, yaml::isEnabled);
    }

    public long getScanIntervalMs() {
        return platformConfig.getLong(SysConfigKeys.ALERT_SCAN_INTERVAL_MS, yaml::getScanIntervalMs);
    }

    public int getCooldownMinutes() {
        return platformConfig.getInt(SysConfigKeys.ALERT_COOLDOWN_MINUTES, yaml::getCooldownMinutes);
    }

    public int getWindowMinutes() {
        return platformConfig.getInt(SysConfigKeys.ALERT_WINDOW_MINUTES, yaml::getWindowMinutes);
    }

    public int getMinExecutions() {
        return platformConfig.getInt(SysConfigKeys.ALERT_MIN_EXECUTIONS, yaml::getMinExecutions);
    }

    public double getSuccessRateThreshold() {
        return platformConfig.getDouble(SysConfigKeys.ALERT_SUCCESS_RATE_THRESHOLD, yaml::getSuccessRateThreshold);
    }

    public int getFailCountThreshold() {
        return platformConfig.getInt(SysConfigKeys.ALERT_FAIL_COUNT_THRESHOLD, yaml::getFailCountThreshold);
    }

    public long getP95CostMsThreshold() {
        return platformConfig.getLong(SysConfigKeys.ALERT_P95_COST_MS_THRESHOLD, yaml::getP95CostMsThreshold);
    }

    public int getScheduleFailThreshold() {
        return platformConfig.getInt(SysConfigKeys.ALERT_SCHEDULE_FAIL_THRESHOLD, yaml::getScheduleFailThreshold);
    }

    public boolean isAlertNoOnlineExecutor() {
        return platformConfig.getBoolean(SysConfigKeys.ALERT_NO_ONLINE_EXECUTOR, yaml::isAlertNoOnlineExecutor);
    }

    public String getSubjectPrefix() {
        return platformConfig.getString(SysConfigKeys.ALERT_SUBJECT_PREFIX, yaml::getSubjectPrefix);
    }
}
