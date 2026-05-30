package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ZestComponent("message")
public class MessageHandler {

    @ZestExecute(value = "sendSms", name = "发送短信")
    public Map<String, Object> sendSms(ChainContext ctx) {
        log.info("消息-发送短信");
        return Map.of("msgId", "SMS" + System.currentTimeMillis(), "status", "SENT");
    }

    @ZestExecute(value = "sendEmail", name = "发送邮件")
    public Map<String, Object> sendEmail(ChainContext ctx) {
        log.info("消息-发送邮件");
        return Map.of("msgId", "EML" + System.currentTimeMillis(), "status", "SENT");
    }

    @ZestExecute(value = "sendAppPush", name = "发送应用推送")
    public Map<String, Object> sendAppPush(ChainContext ctx) {
        log.info("消息-应用推送");
        return Map.of("msgId", "PSH" + System.currentTimeMillis(), "channel", "JPUSH");
    }

    @ZestExecute(value = "sendWechatMsg", name = "发送微信消息")
    public Map<String, Object> sendWechatMsg(ChainContext ctx) {
        log.info("消息-微信消息");
        return Map.of("msgId", "WCH" + System.currentTimeMillis(), "template", "ORDER_PAID");
    }

    @ZestExecute(value = "sendDingtalk", name = "发送钉钉通知")
    public Map<String, Object> sendDingtalk(ChainContext ctx) {
        log.info("消息-钉钉通知");
        return Map.of("msgId", "DIN" + System.currentTimeMillis(), "robot", "告警机器人");
    }

    @ZestExecute(value = "batchSendSms", name = "批量发送短信")
    public Map<String, Object> batchSendSms(ChainContext ctx) {
        log.info("消息-批量短信");
        return Map.of("totalSent", 500, "failedCount", 3);
    }

    @ZestExecute(value = "queryMsgStatus", name = "查询消息状态")
    public Map<String, Object> queryMsgStatus(ChainContext ctx) {
        log.info("消息-查询状态");
        return Map.of("status", "DELIVERED", "readAt", "2026-05-30 12:00:00");
    }

    @ZestExecute(value = "buildMsgTemplate", name = "构建消息模板")
    public Map<String, Object> buildMsgTemplate(ChainContext ctx) {
        log.info("消息-构建模板");
        return Map.of("templateId", "TPL_ORDER_NOTIFY", "params", Map.of("name", "张三", "orderNo", "ORD001"));
    }

    @ZestExecute(value = "subscribeMsg", name = "订阅消息")
    public Map<String, Object> subscribeMsg(ChainContext ctx) {
        log.info("消息-订阅消息");
        return Map.of("userId", 10001, "topics", new String[]{"ORDER_UPDATE", "PROMOTION"});
    }

    @ZestExecute(value = "cancelMsg", name = "取消消息发送")
    public Map<String, Object> cancelMsg(ChainContext ctx) {
        log.info("消息-取消发送");
        return Map.of("result", "cancelled", "cancelledIds", new String[]{"MSG001", "MSG002"});
    }
}
