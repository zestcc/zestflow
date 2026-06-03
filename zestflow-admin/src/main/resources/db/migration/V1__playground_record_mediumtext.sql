-- 2026-06-03：试验场大链执行结果可能超过 TEXT(64KB)，扩列为 MEDIUMTEXT（对标 xxl-job 执行日志 MEDIUMTEXT）
ALTER TABLE `playground_record`
    MODIFY COLUMN `request_body`  MEDIUMTEXT DEFAULT NULL COMMENT '请求体 JSON',
    MODIFY COLUMN `response_body` MEDIUMTEXT DEFAULT NULL COMMENT '响应体 JSON';
