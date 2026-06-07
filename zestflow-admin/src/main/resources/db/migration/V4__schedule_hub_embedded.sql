-- 调度 Hub 化：停用 Admin 侧业务 Cron 扫描（改由 Executor 嵌入式驱动）

UPDATE `schedule` SET `status` = 0, `remark` = CONCAT(IFNULL(`remark`, ''), ' [deprecated: moved to Executor embedded]')
WHERE `job_key` = 'admin.schedule.scan';
