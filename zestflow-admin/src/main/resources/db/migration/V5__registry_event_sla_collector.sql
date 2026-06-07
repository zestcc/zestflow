-- v0.2: 事件驱动注册 + SLA 下沉 Collector
UPDATE schedule SET status = 0, remark = CONCAT(IFNULL(remark, ''), ' [v0.2 已停用]')
WHERE job_key IN (
    'admin.registry.heartbeat-flush',
    'admin.registry.offline-check',
    'admin.alert.execution-sla'
);

INSERT INTO schedule (job_type, job_key, chain_code, chain_name, cron, schedule_kind, fixed_interval_ms,
                      module, remote, editable, status, remark, created_at, updated_at)
SELECT 'PLATFORM', 'collector.alert.execution-sla', 'collector.alert.execution-sla', '执行 SLA 邮件告警',
       '每 5 分钟', 'FIXED_RATE', 300000, 'collector', 1, 0, 1,
       'Collector 扫描 chain_event，Admin 负责配置/冷却/发信', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM schedule WHERE job_key = 'collector.alert.execution-sla');
