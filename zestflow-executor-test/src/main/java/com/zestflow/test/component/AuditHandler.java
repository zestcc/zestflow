package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ZestComponent("audit")
public class AuditHandler {

    @ZestExecute(value = "submitAudit", name = "提交审核")
    public Map<String, Object> submitAudit(ChainContext ctx) {
        log.info("审核-提交审核");
        return Map.of("auditNo", "AUD" + System.currentTimeMillis(), "status", "PENDING");
    }

    @ZestExecute(value = "autoApprove", name = "自动审批")
    public Map<String, Object> autoApprove(ChainContext ctx) {
        log.info("审核-自动审批");
        return Map.of("approved", true, "rule", "AMOUNT_LT_1000");
    }

    @ZestExecute(value = "manualAudit", name = "人工审核")
    public Map<String, Object> manualAudit(ChainContext ctx) {
        log.info("审核-人工审核");
        return Map.of("auditor", "管理员", "opinion", "通过", "level", "L2");
    }

    @ZestExecute(value = "rejectAudit", name = "驳回审核")
    public Map<String, Object> rejectAudit(ChainContext ctx) {
        log.info("审核-驳回");
        return Map.of("result", "rejected", "reason", "资料不完整");
    }

    @ZestExecute(value = "recallAudit", name = "撤回审核")
    public Map<String, Object> recallAudit(ChainContext ctx) {
        log.info("审核-撤回");
        return Map.of("result", "recalled", "auditNo", ctx.get("auditNo"));
    }

    @ZestExecute(value = "assignAuditor", name = "分配审核人")
    public Map<String, Object> assignAuditor(ChainContext ctx) {
        log.info("审核-分配审核人");
        return Map.of("auditorId", 2001, "auditorName", "钱七");
    }

    @ZestExecute(value = "queryAuditLog", name = "查询审核日志")
    public Map<String, Object> queryAuditLog(ChainContext ctx) {
        log.info("审核-查询日志");
        return Map.of("logs", new String[]{"提交审核 2026-05-30 10:00", "审批通过 2026-05-30 11:00"});
    }

    @ZestExecute(value = "escalateAudit", name = "升级审核")
    public Map<String, Object> escalateAudit(ChainContext ctx) {
        log.info("审核-升级处理");
        return Map.of("fromLevel", "L1", "toLevel", "L3", "reason", "金额超限");
    }

    @ZestExecute(value = "batchAudit", name = "批量审核")
    public Map<String, Object> batchAudit(ChainContext ctx) {
        log.info("审核-批量审核");
        return Map.of("total", 20, "approved", 18, "rejected", 2);
    }

    @ZestExecute(value = "auditNotify", name = "审核结果通知")
    public Map<String, Object> auditNotify(ChainContext ctx) {
        log.info("审核-结果通知");
        return Map.of("notified", true, "channel", "EMAIL");
    }
}
