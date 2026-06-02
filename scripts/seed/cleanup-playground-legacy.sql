-- 2026-06-02：清理游离 CHN_PLAYGROUND_* 与 playground-app 种子（已有库执行一次）

USE `zestflow_app_bussiness`;
DELETE FROM `zf_chain_version` WHERE `chain_code` LIKE 'CHN_PLAYGROUND_%' OR `chain_code` LIKE 'CHN_DEMO_%';
DELETE FROM `zf_design_binding` WHERE `chain_code` LIKE 'CHN_PLAYGROUND_%' OR `chain_code` LIKE 'CHN_DEMO_%'
  OR `design_code` LIKE 'DES_PLAYGROUND_%' OR `design_code` LIKE 'DES_DEMO_%';
DELETE FROM `zf_design` WHERE `code` LIKE 'DES_PLAYGROUND_%' OR `code` LIKE 'DES_DEMO_%';
DELETE FROM `zf_chain` WHERE `code` LIKE 'CHN_PLAYGROUND_%' OR `code` LIKE 'CHN_DEMO_%' OR `app_code` = 'playground-app';

USE `zestflow_admin`;
DELETE FROM `playground_scene` WHERE `app_code` = 'demo-app' OR `chain_code` LIKE 'CHN_DEMO_%' OR `chain_code` LIKE 'CHN_PLAYGROUND_%';
