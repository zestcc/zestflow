-- V5：业务表唯一约束改为 (tenant_id, …)，支持多租户克隆与按租户查重
USE `zestflow_admin`;

ALTER TABLE `role`
    DROP INDEX `uk_code`,
    ADD UNIQUE KEY `uk_tenant_role` (`tenant_id`, `code`);

ALTER TABLE `user_app_role`
    DROP INDEX `uk_user_app`,
    ADD UNIQUE KEY `uk_tenant_user_app` (`tenant_id`, `user_id`, `app_code`);

ALTER TABLE `sys_dict_type`
    DROP INDEX `uk_code`,
    ADD UNIQUE KEY `uk_tenant_dict_type` (`tenant_id`, `code`);

ALTER TABLE `sys_dict_data`
    ADD UNIQUE KEY `uk_tenant_dict_data` (`tenant_id`, `type_code`, `value`);

ALTER TABLE `executor_registry`
    DROP INDEX `uk_executor_id`,
    ADD UNIQUE KEY `uk_tenant_executor` (`tenant_id`, `executor_id`);

ALTER TABLE `collector_registry`
    DROP INDEX `uk_collector_id`,
    ADD UNIQUE KEY `uk_tenant_collector` (`tenant_id`, `collector_id`);

ALTER TABLE `component`
    DROP INDEX `uk_executor_component`,
    ADD UNIQUE KEY `uk_tenant_executor_component` (`tenant_id`, `executor_id`, `component_id`);
