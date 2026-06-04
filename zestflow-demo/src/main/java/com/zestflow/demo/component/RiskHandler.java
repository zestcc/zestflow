package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.annotation.ZestPredicate;
import com.zestflow.executor.annotation.ZestTag;
import com.zestflow.executor.annotation.ZestTags;
import com.zestflow.demo.component.model.risk.RiskResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("risk")
public class RiskHandler {

    @ZestPredicate(value = "riskCheckUser", name = "用户风控判断")
    @ZestTags({
            @ZestTag(name = "用户正常", value = "true"),
            @ZestTag(name = "用户异常", value = "false")
    })
    public boolean riskCheckUser(@ZestParam(value = "userId", required = false) String userId) {
        log.info("风控-用户风控判断 userId={}", userId);
        return true;
    }

    @ZestPredicate(value = "riskCheckDevice", name = "设备风控判断")
    @ZestTags({
            @ZestTag(name = "设备可信", value = "true"),
            @ZestTag(name = "设备异常", value = "false")
    })
    public boolean riskCheckDevice(@ZestParam(value = "deviceId", required = false) String deviceId) {
        log.info("风控-设备风控判断 deviceId={}", deviceId);
        return true;
    }

    @ZestPredicate(value = "riskCheckIp", name = "IP风控判断")
    public boolean riskCheckIp(@ZestParam(value = "clientIp", required = false) String clientIp) {
        log.info("风控-IP风控判断 clientIp={}", clientIp);
        return false;
    }

    @ZestPredicate(value = "riskCheckAmount", name = "金额风控判断")
    @ZestTags({
            @ZestTag(name = "通过", value = "true"),
            @ZestTag(name = "拦截", value = "false")
    })
    public boolean riskCheckAmount(@ZestParam(value = "amount", defaultValue = "0") double amount) {
        log.info("风控-金额风控判断 amount={}", amount);
        return true;
    }

    @ZestPredicate(value = "riskCheckFrequency", name = "频率风控判断")
    public boolean riskCheckFrequency(@ZestParam(value = "userId", required = false) String userId) {
        log.info("风控-频率风控判断 userId={}", userId);
        return true;
    }

    @ZestExecute(value = "recordRiskEvent", name = "记录风控事件")
    public RiskResults.RecordRiskEventResult recordRiskEvent() {
        log.info("风控-记录风控事件");
        return new RiskResults.RecordRiskEventResult("RSK" + System.currentTimeMillis(), "LOW");
    }

    @ZestExecute(value = "blockUser", name = "冻结用户")
    public RiskResults.BlockUserResult blockUser(
            @ZestParam(value = "userId", defaultValue = "U-RISK") String userId) {
        log.info("风控-冻结用户 userId={}", userId);
        return new RiskResults.BlockUserResult(userId, true, "异常操作");
    }

    @ZestExecute(value = "applyAntiFraud", name = "反欺诈校验")
    public RiskResults.ApplyAntiFraudResult applyAntiFraud() {
        log.info("风控-反欺诈校验");
        return new RiskResults.ApplyAntiFraudResult(20, "PASS");
    }

    @ZestExecute(value = "calcRiskScore", name = "计算风险评分")
    public RiskResults.CalcRiskScoreResult calcRiskScore() {
        log.info("风控-风险评分");
        return new RiskResults.CalcRiskScoreResult(65, "MEDIUM", new String[]{"异地登录", "新设备"});
    }

    @ZestExecute(value = "submitManualReview", name = "提交人工复审")
    public RiskResults.SubmitManualReviewResult submitManualReview() {
        log.info("风控-提交人工复审");
        return new RiskResults.SubmitManualReviewResult("REV" + System.currentTimeMillis(), "PENDING_REVIEW");
    }
}
