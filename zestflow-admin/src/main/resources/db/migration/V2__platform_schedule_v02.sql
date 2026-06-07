-- V2 — 平台调度 v0.2 对齐（幂等：可重复执行）
-- 停用 Admin 侧已迁移至 Executor/Collector 的平台任务，并注册 Collector SLA 任务

UPDATE `schedule` SET `status` = 0,
    `remark` = CASE
        WHEN IFNULL(`remark`, '') LIKE '%deprecated: moved to Executor embedded%' THEN `remark`
        ELSE CONCAT(IFNULL(`remark`, ''), ' [deprecated: moved to Executor embedded]')
    END
WHERE `job_key` = 'admin.schedule.scan'
  AND (IFNULL(`status`, 1) <> 0 OR IFNULL(`remark`, '') NOT LIKE '%deprecated: moved to Executor embedded%');

UPDATE `schedule` SET `status` = 0,
    `remark` = CASE
        WHEN IFNULL(`remark`, '') LIKE '%[v0.2 已停用]%' THEN `remark`
        ELSE CONCAT(IFNULL(`remark`, ''), ' [v0.2 已停用]')
    END
WHERE `job_key` IN (
    'admin.registry.heartbeat-flush',
    'admin.registry.offline-check',
    'admin.alert.execution-sla'
) AND (IFNULL(`status`, 1) <> 0 OR IFNULL(`remark`, '') NOT LIKE '%[v0.2 已停用]%');

INSERT INTO `schedule` (`job_type`, `job_key`, `chain_code`, `chain_name`, `cron`, `schedule_kind`, `fixed_interval_ms`,
                        `module`, `remote`, `editable`, `status`, `remark`, `created_at`, `updated_at`)
SELECT 'PLATFORM', 'collector.alert.execution-sla', 'collector.alert.execution-sla', '执行 SLA 邮件告警',
       '每 5 分钟', 'FIXED_RATE', 300000, 'collector', 1, 0, 1,
       'Collector 扫描 chain_event，Admin 负责配置/冷却/发信', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `schedule` WHERE `job_key` = 'collector.alert.execution-sla');
