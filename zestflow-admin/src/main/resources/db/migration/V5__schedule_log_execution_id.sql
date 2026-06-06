-- V5：调度日志关联链执行 ID，便于跳转 Collector 执行轨迹
ALTER TABLE `schedule_log`
    ADD COLUMN `execution_id` VARCHAR(64) DEFAULT NULL COMMENT '链执行追踪 ID（CHAIN 调度触发后回写）' AFTER `executor_address`,
    ADD KEY `idx_schedule_log_execution_id` (`execution_id`);
