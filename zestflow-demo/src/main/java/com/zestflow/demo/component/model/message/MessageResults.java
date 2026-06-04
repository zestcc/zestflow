package com.zestflow.demo.component.model.message;

/** 消息域元件返回值。 */
public final class MessageResults {
    private MessageResults() {}

    public record SendSmsResult(String msgId, String status) {}
    public record SendEmailResult(String msgId, String status) {}
    public record SendAppPushResult(String msgId, String channel) {}
    public record SendWechatMsgResult(String msgId, String template) {}
    public record SendDingtalkResult(String msgId, String robot) {}
    public record BatchSendSmsResult(int totalSent, int failedCount) {}
    public record QueryMsgStatusResult(String status, String readAt) {}
    public record BuildMsgTemplateResult(String templateId, String params) {}
    public record SubscribeMsgResult(String userId, String[] topics) {}
    public record CancelMsgResult(String result, String[] cancelledIds) {}
}
