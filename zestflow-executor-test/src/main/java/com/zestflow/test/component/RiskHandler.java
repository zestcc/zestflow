package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestPredicate;
import com.zestflow.executor.annotation.ZestTag;
import com.zestflow.executor.annotation.ZestTags;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ZestComponent("risk")
public class RiskHandler {

    @ZestPredicate(value = "riskCheckUser", name = "用户风控判断")
    @ZestTags({
        @ZestTag(name="用户正常", value="true"),
        @ZestTag(name="用户异常", value="false")
    })
    public boolean riskCheckUser(ChainContext ctx) {
        log.info("风控-用户风控判断");
        return true;
    }

    @ZestPredicate(value = "riskCheckDevice", name = "设备风控判断")
    @ZestTags({
        @ZestTag(name="设备可信", value="true"),
        @ZestTag(name="设备异常", value="false")
    })
    public boolean riskCheckDevice(ChainContext ctx) {
        log.info("风控-设备风控判断");
        return true;
    }

    @ZestPredicate(value = "riskCheckIp", name = "IP风控判断")
    public boolean riskCheckIp(ChainContext ctx) {
        log.info("风控-IP风控判断");
        return false;
    }

    @ZestPredicate(value = "riskCheckAmount", name = "金额风控判断")
    public boolean riskCheckAmount(ChainContext ctx) {
        log.info("风控-金额风控判断");
        return true;
    }

    @ZestPredicate(value = "riskCheckFrequency", name = "频率风控判断")
    public boolean riskCheckFrequency(ChainContext ctx) {
        log.info("风控-频率风控判断");
        return true;
    }

    @ZestExecute(value = "recordRiskEvent", name = "记录风控事件")
    public Map<String, Object> recordRiskEvent(ChainContext ctx) {
        log.info("风控-记录风控事件");
        return Map.of("eventId", "RSK" + System.currentTimeMillis(), "level", "LOW");
    }

    @ZestExecute(value = "blockUser", name = "冻结用户")
    public Map<String, Object> blockUser(ChainContext ctx) {
        log.info("风控-冻结用户");
        return Map.of("userId", ctx.get("userId"), "blocked", true, "reason", "异常操作");
    }

    @ZestExecute(value = "applyAntiFraud", name = "反欺诈校验")
    public Map<String, Object> applyAntiFraud(ChainContext ctx) {
        log.info("风控-反欺诈校验");
        return Map.of("riskScore", 20, "suggestion", "PASS");
    }

    @ZestExecute(value = "calcRiskScore", name = "计算风险评分")
    public Map<String, Object> calcRiskScore(ChainContext ctx) {
        log.info("风控-风险评分");
        return Map.of("score", 65, "level", "MEDIUM", "factors", new String[]{"异地登录", "新设备"});
    }

    @ZestExecute(value = "submitManualReview", name = "提交人工复审")
    public Map<String, Object> submitManualReview(ChainContext ctx) {
        log.info("风控-提交人工复审");
        return Map.of("reviewNo", "REV" + System.currentTimeMillis(), "status", "PENDING_REVIEW");
    }
}
