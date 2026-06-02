-- ============================================================================
-- ZestFlow Admin 种子数据
-- 职责：Admin 模块种子数据（租户、用户-租户关联、角色、演示场景）
-- 用法：在 init.sql 之后执行（或 Flyway 迁移时 include），使用对应数据库后再操作
-- 2026-06-02
-- ============================================================================

USE `zestflow_admin`;

-- ==================== 系统母版租户（tenant） ====================

INSERT IGNORE INTO `tenant` (`id`, `name`, `code`, `description`, `status`)
VALUES (1, '系统母版', 'system-template', '模板租户，新租户从此拷贝初始数据', 1);

-- 2026-06-02：E2E 租户 B（多租户 / IP 演示隔离验收）
INSERT IGNORE INTO `tenant` (`id`, `name`, `code`, `description`, `status`)
VALUES (2, 'E2E演示租户B', 'e2e-tenant-b', '企业级 E2E 多租户与 IP 映射验收专用', 1);

-- ==================== 用户-租户关联（user_tenant） ====================
-- 默认 admin 用户（id=1）绑定到系统母版租户，作为租户管理员

INSERT IGNORE INTO `user_tenant` (`user_id`, `tenant_id`, `is_tenant_admin`, `created_by`)
SELECT 1, 1, 1, 'system'
WHERE EXISTS (SELECT 1 FROM `user` WHERE id = 1);

INSERT IGNORE INTO `user_tenant` (`user_id`, `tenant_id`, `is_tenant_admin`, `created_by`)
SELECT 1, 2, 1, 'system'
WHERE EXISTS (SELECT 1 FROM `user` WHERE id = 1);

-- 2026-06-02：IP → 租户映射（需 zestflow.tenant.mode=multi 且 ip-demo-mode=enabled）
INSERT IGNORE INTO `tenant_ip_mapping` (`ip_address`, `tenant_id`)
VALUES ('10.0.0.101', 2),
       ('10.0.0.102', 1);

-- ==================== 角色（role） ====================
-- role 表的 INSERT 已内嵌在 DDL 中，此处无额外数据

-- ==================== 演示场景（playground_scene） ====================
-- demo-app playground scenes (28 full-chain)

-- demo-app playground scenes (28 full-chain)

-- demo-app playground scenes (28 full-chain)

-- demo-app playground scenes (28 full-chain)

-- demo-app playground scenes (28 full-chain)


INSERT IGNORE INTO `playground_scene` (`scene_code`, `name`, `description`, `request_path`, `request_method`, `body_type`, `request_body`, `response_example`, `chain_code`, `rate_limit`, `tenant_id`, `app_code`, `created_by`, `updated_by`, `created_at`, `updated_at`) VALUES
('SCN20260531000001', '单节点', '单节点', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_NODE_1', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531000002', '双节点', '双节点', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_NODE_2', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531000003', '脚本节点', '脚本节点', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_SCRIPT_GATE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531000004', '75步压力链', '75步压力链', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_STRESS_75', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010001', '订单创建', '订单创建', '/execute', 'POST', 'JSON', '{"userId":"U10086","sku":"SKU9981","quantity":2}', '{"code":200}', 'CHN_DEMO_ORDER_CREATE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010002', '订单支付', '订单支付', '/execute', 'POST', 'JSON', '{"orderId":"ORD202605310001","userId":"U10086"}', '{"code":200}', 'CHN_DEMO_ORDER_PAY', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010003', '订单退款', '订单退款', '/execute', 'POST', 'JSON', '{"orderId":"ORD202605310001","userId":"U10086"}', '{"code":200}', 'CHN_DEMO_ORDER_REFUND', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010004', '订单取消', '订单取消', '/execute', 'POST', 'JSON', '{"orderId":"ORD202605310001","userId":"U10086"}', '{"code":200}', 'CHN_DEMO_ORDER_CANCEL', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010005', '订单评价', '订单评价', '/execute', 'POST', 'JSON', '{"orderId":"ORD202605310001","userId":"U10086","rating":5}', '{"code":200}', 'CHN_DEMO_ORDER_REVIEW', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010006', '售后申请', '售后申请', '/execute', 'POST', 'JSON', '{"orderId":"ORD202605310001","userId":"U10086","type":"RETURN"}', '{"code":200}', 'CHN_DEMO_AFTER_SALE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010007', '支付风控分支', '支付风控分支', '/execute', 'POST', 'JSON', '{"userId":"U10086","amount":199}', '{"code":200}', 'CHN_DEMO_PAYMENT_RISK', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010008', '订单全链路', '订单全链路', '/execute', 'POST', 'JSON', '{"userId":"U10086","sku":"SKU9981"}', '{"code":200}', 'CHN_DEMO_ORDER_PIPELINE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010009', '子链下单', '子链下单', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_SUB_ORDER', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531010010', '迭代通知', '迭代通知', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_ITER_NOTIFY', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531020001', '商品入库', '商品入库', '/execute', 'POST', 'JSON', '{"sku":"SKU9981","quantity":100}', '{"code":200}', 'CHN_DEMO_STOCK_IN', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531020002', '商品出库', '商品出库', '/execute', 'POST', 'JSON', '{"orderId":"ORD202605310001","sku":"SKU9981"}', '{"code":200}', 'CHN_DEMO_STOCK_OUT', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531020003', '库存盘点', '库存盘点', '/execute', 'POST', 'JSON', '{"warehouseCode":"WH001"}', '{"code":200}', 'CHN_DEMO_STOCK_CHECK', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531020004', '库存调拨', '库存调拨', '/execute', 'POST', 'JSON', '{"sku":"SKU9981","qty":10}', '{"code":200}', 'CHN_DEMO_STOCK_TRANSFER', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531020005', '物流发货', '物流发货', '/execute', 'POST', 'JSON', '{"orderId":"ORD202605310001"}', '{"code":200}', 'CHN_DEMO_LOGISTICS_SHIP', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531030001', '会员注册', '会员注册', '/execute', 'POST', 'JSON', '{"userId":"U10087"}', '{"code":200}', 'CHN_DEMO_MEMBER_REGISTER', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531030002', '会员升级', '会员升级', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_MEMBER_UPGRADE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531030003', '积分累计', '积分累计', '/execute', 'POST', 'JSON', '{"userId":"U10086","amount":179}', '{"code":200}', 'CHN_DEMO_POINTS_EARN', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531030004', '积分兑换', '积分兑换', '/execute', 'POST', 'JSON', '{"userId":"U10086","costPoints":200}', '{"code":200}', 'CHN_DEMO_POINTS_REDEEM', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531030005', '会员充值', '会员充值', '/execute', 'POST', 'JSON', '{"userId":"U10086","amount":500}', '{"code":200}', 'CHN_DEMO_MEMBER_TOPUP', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531030006', '等级计算', '等级计算', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_LEVEL_CALC', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531040001', '支付回调', '支付回调', '/execute', 'POST', 'JSON', '{"orderId":"ORD202605310001"}', '{"code":200}', 'CHN_DEMO_PAYMENT_CALLBACK', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531040002', '账单生成', '账单生成', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_BILL_GEN', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531040003', '对账处理', '对账处理', '/execute', 'POST', 'JSON', '{"bizDate":"2026-05-31"}', '{"code":200}', 'CHN_DEMO_RECONCILE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531040004', '发票开具', '发票开具', '/execute', 'POST', 'JSON', '{"orderId":"ORD202605310001","userId":"U10086"}', '{"code":200}', 'CHN_DEMO_INVOICE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531050001', '优惠券发放', '优惠券发放', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_COUPON_ISSUE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531050002', '优惠券核销', '优惠券核销', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_COUPON_USE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531050003', '秒杀活动', '秒杀活动', '/execute', 'POST', 'JSON', '{"userId":"U10086","sku":"SKU9981"}', '{"code":200}', 'CHN_DEMO_FLASH_SALE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531050004', '满减计算', '满减计算', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_PROMO_CALC', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531050005', '营销多分支', '营销多分支', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_MARKETING_BRANCH', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531060001', '短信发送', '短信发送', '/execute', 'POST', 'JSON', '{"userId":"U10086","phone":"13800008888"}', '{"code":200}', 'CHN_DEMO_SMS_SEND', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260531060002', '邮件通知', '邮件通知', '/execute', 'POST', 'JSON', '{"userId":"U10086","to":"user@example.com"}', '{"code":200}', 'CHN_DEMO_EMAIL_SEND', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260601000229', '售后单处理', '售后单处理', '/api/orders/handleApplyAfterSale', 'POST', 'JSON', '{"applyId":"BB-PG-001"}', '{"code":200}', 'CHN_DEMO_AFTER_SALE', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260602000001', '失败继续', '失败继续', '/execute', 'POST', 'JSON', '{"userId":"U10086"}', '{"code":200}', 'CHN_DEMO_CONTINUE_ON_ERROR', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW()),
('SCN20260602000002', '租户B专属', '多租户E2E隔离', '/execute', 'POST', 'JSON', '{"userId":"U-TENANT-B"}', '{"code":200}', 'CHN_DEMO_NODE_1', 30, 2, 'demo-app', 'system', 'system', NOW(), NOW());
