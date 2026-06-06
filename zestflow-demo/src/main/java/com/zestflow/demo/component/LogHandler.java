package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 日志处理类元件示例
 * 覆盖 LOGGER 和 DELAY 相关场景
 */
@Slf4j
@ZestComponent("log")
public class LogHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @ZestExecute(value = "logInfo", name = "记录INFO日志")
    public void logInfo(@ZestParam(value = "message") String message) {
        log.info("[LOGGER] {}", message);
    }

    @ZestExecute(value = "logWarn", name = "记录WARN日志")
    public void logWarn(@ZestParam(value = "message") String message) {
        log.warn("[LOGGER] {}", message);
    }

    @ZestExecute(value = "logError", name = "记录ERROR日志")
    public void logError(@ZestParam(value = "message") String message) {
        log.error("[LOGGER] {}", message);
    }

    @ZestExecute(value = "logDebug", name = "记录DEBUG日志")
    public void logDebug(@ZestParam(value = "message") String message) {
        log.debug("[LOGGER] {}", message);
    }

    @ZestExecute(value = "logContextInfo", name = "记录上下文信息")
    public void logContextInfo(ChainContext ctx) {
        String userId = ctx.get("userId", String.class);
        String orderId = ctx.get("orderId", String.class);
        String traceId = ctx.get("_traceId", String.class);
        log.info("[CONTEXT] userId={} orderId={} traceId={}", userId, orderId, traceId);
    }

    @ZestExecute(value = "logBusinessMetrics", name = "记录业务指标")
    public void logBusinessMetrics(
            @ZestParam(value = "businessType") String businessType,
            @ZestParam(value = "success") boolean success,
            @ZestParam(value = "costMs") long costMs) {
        log.info("[METRICS] businessType={} success={} costMs={}", businessType, success, costMs);
    }

    @ZestExecute(value = "logOrderCreated", name = "记录订单创建日志")
    public void logOrderCreated(
            @ZestParam(value = "orderId") String orderId,
            @ZestParam(value = "userId") String userId,
            @ZestParam(value = "amount") double amount) {
        String time = LocalDateTime.now().format(FORMATTER);
        log.info("[ORDER_CREATED] time={} orderId={} userId={} amount={}", time, orderId, userId, amount);
    }

    @ZestExecute(value = "logPaymentSuccess", name = "记录支付成功日志")
    public void logPaymentSuccess(
            @ZestParam(value = "paymentId") String paymentId,
            @ZestParam(value = "orderId") String orderId,
            @ZestParam(value = "amount") double amount,
            @ZestParam(value = "payType") String payType) {
        String time = LocalDateTime.now().format(FORMATTER);
        log.info("[PAYMENT_SUCCESS] time={} paymentId={} orderId={} amount={} payType={}",
                time, paymentId, orderId, amount, payType);
    }

    @ZestExecute(value = "logRiskAlert", name = "记录风险告警")
    public void logRiskAlert(
            @ZestParam(value = "riskType") String riskType,
            @ZestParam(value = "riskScore") int riskScore,
            @ZestParam(value = "userId", required = false) String userId) {
        String time = LocalDateTime.now().format(FORMATTER);
        log.warn("[RISK_ALERT] time={} riskType={} riskScore={} userId={}", time, riskType, riskScore, userId);
    }

    @ZestExecute(value = "logApiCall", name = "记录API调用")
    public void logApiCall(
            @ZestParam(value = "apiName") String apiName,
            @ZestParam(value = "method", defaultValue = "GET") String method,
            @ZestParam(value = "statusCode", defaultValue = "200") int statusCode) {
        String time = LocalDateTime.now().format(FORMATTER);
        log.info("[API_CALL] time={} apiName={} method={} statusCode={}", time, apiName, method, statusCode);
    }

    @ZestExecute(value = "logSagaCompensation", name = "记录Saga补偿")
    public void logSagaCompensation(
            @ZestParam(value = "nodeId") String nodeId,
            @ZestParam(value = "compensationType") String compensationType,
            @ZestParam(value = "success") boolean success) {
        String time = LocalDateTime.now().format(FORMATTER);
        log.warn("[SAGA_COMPENSATION] time={} nodeId={} type={} success={}", time, nodeId, compensationType, success);
    }

    @ZestExecute(value = "delayMs", name = "延迟指定毫秒")
    public void delayMs(@ZestParam(value = "milliseconds", defaultValue = "1000") long milliseconds) {
        try {
            log.debug("[DELAY] sleeping for {}ms", milliseconds);
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[DELAY] interrupted");
        }
    }

    @ZestExecute(value = "delaySeconds", name = "延迟指定秒数")
    public void delaySeconds(@ZestParam(value = "seconds", defaultValue = "1") int seconds) {
        try {
            long milliseconds = seconds * 1000L;
            log.debug("[DELAY] sleeping for {}s ({}ms)", seconds, milliseconds);
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[DELAY] interrupted");
        }
    }

    @ZestExecute(value = "delayRandom", name = "随机延迟")
    public void delayRandom(
            @ZestParam(value = "minMs", defaultValue = "500") long minMs,
            @ZestParam(value = "maxMs", defaultValue = "2000") long maxMs) {
        long delay = minMs + (long) (Math.random() * (maxMs - minMs));
        try {
            log.debug("[DELAY] random sleeping for {}ms", delay);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[DELAY] interrupted");
        }
    }

    @ZestExecute(value = "logFullContext", name = "记录完整上下文")
    public void logFullContext(ChainContext ctx) {
        Map<String, Object> snapshot = ctx.snapshot();
        log.info("[FULL_CONTEXT] {}", snapshot);
    }

    @ZestExecute(value = "logStep", name = "记录步骤")
    public void logStep(
            @ZestParam(value = "step") String step,
            @ZestParam(value = "status", defaultValue = "START") String status) {
        String time = LocalDateTime.now().format(FORMATTER);
        log.info("[STEP] time={} step={} status={}", time, step, status);
    }
}