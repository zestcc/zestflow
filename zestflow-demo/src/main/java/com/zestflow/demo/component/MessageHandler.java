package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.demo.component.model.message.MessageResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("message")
public class MessageHandler {

    @ZestExecute(value = "sendSms", name = "发送短信")
    public MessageResults.SendSmsResult sendSms(
            @ZestParam(value = "userId", required = false) String userId) {
        log.info("消息-发送短信 userId={}", userId);
        return new MessageResults.SendSmsResult("SMS" + System.currentTimeMillis(), "SENT");
    }

    @ZestExecute(value = "sendEmail", name = "发送邮件")
    public MessageResults.SendEmailResult sendEmail() {
        log.info("消息-发送邮件");
        return new MessageResults.SendEmailResult("EML" + System.currentTimeMillis(), "SENT");
    }

    @ZestExecute(value = "sendAppPush", name = "发送应用推送")
    public MessageResults.SendAppPushResult sendAppPush() {
        log.info("消息-应用推送");
        return new MessageResults.SendAppPushResult("PSH" + System.currentTimeMillis(), "JPUSH");
    }

    @ZestExecute(value = "sendWechatMsg", name = "发送微信消息")
    public MessageResults.SendWechatMsgResult sendWechatMsg() {
        log.info("消息-微信消息");
        return new MessageResults.SendWechatMsgResult("WCH" + System.currentTimeMillis(), "ORDER_PAID");
    }

    @ZestExecute(value = "sendDingtalk", name = "发送钉钉通知")
    public MessageResults.SendDingtalkResult sendDingtalk() {
        log.info("消息-钉钉通知");
        return new MessageResults.SendDingtalkResult("DIN" + System.currentTimeMillis(), "告警机器人");
    }

    @ZestExecute(value = "batchSendSms", name = "批量发送短信")
    public MessageResults.BatchSendSmsResult batchSendSms() {
        log.info("消息-批量短信");
        return new MessageResults.BatchSendSmsResult(500, 3);
    }

    @ZestExecute(value = "queryMsgStatus", name = "查询消息状态")
    public MessageResults.QueryMsgStatusResult queryMsgStatus(
            @ZestParam(value = "msgId", required = false) String msgId) {
        log.info("消息-查询状态 msgId={}", msgId);
        return new MessageResults.QueryMsgStatusResult("DELIVERED", "2026-05-30 12:00:00");
    }

    @ZestExecute(value = "buildMsgTemplate", name = "构建消息模板")
    public MessageResults.BuildMsgTemplateResult buildMsgTemplate() {
        log.info("消息-构建模板");
        return new MessageResults.BuildMsgTemplateResult("TPL_ORDER_NOTIFY", "name=张三,orderNo=ORD001");
    }

    @ZestExecute(value = "subscribeMsg", name = "订阅消息")
    public MessageResults.SubscribeMsgResult subscribeMsg(
            @ZestParam(value = "userId", defaultValue = "10001") String userId) {
        log.info("消息-订阅消息 userId={}", userId);
        return new MessageResults.SubscribeMsgResult(userId, new String[]{"ORDER_UPDATE", "PROMOTION"});
    }

    @ZestExecute(value = "cancelMsg", name = "取消消息发送")
    public MessageResults.CancelMsgResult cancelMsg() {
        log.info("消息-取消发送");
        return new MessageResults.CancelMsgResult("cancelled", new String[]{"MSG001", "MSG002"});
    }
}
