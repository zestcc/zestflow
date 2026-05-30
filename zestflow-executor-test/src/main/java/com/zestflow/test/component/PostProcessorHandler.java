package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestPostProcessor;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 后置处理器测试
 */
@Slf4j
@ZestComponent("postprocessor")
public class PostProcessorHandler {

    @ZestPostProcessor(value = "postResponseLog", name = "响应日志")
    public void postResponseLog(ChainContext ctx) {
        log.info("后置处理：响应日志");
    }

    @ZestPostProcessor(value = "postDataDesensitize", name = "数据脱敏")
    public void postDataDesensitize(ChainContext ctx) {
        log.info("后置处理：数据脱敏");
    }

    @ZestPostProcessor(value = "postNotifySend", name = "通知发送")
    public void postNotifySend(ChainContext ctx) {
        log.info("后置处理：通知发送");
    }

    @ZestPostProcessor(value = "postAuditLog", name = "审计日志")
    public void postAuditLog(ChainContext ctx) {
        log.info("后置处理：审计日志");
    }

    @ZestPostProcessor(value = "postMetricsCollect", name = "指标采集")
    public void postMetricsCollect(ChainContext ctx) {
        log.info("后置处理：指标采集");
    }

    @ZestPostProcessor(value = "postCacheUpdate", name = "缓存更新")
    public void postCacheUpdate(ChainContext ctx) {
        log.info("后置处理：缓存更新");
    }

    @ZestPostProcessor(value = "postDataSync", name = "数据同步")
    public void postDataSync(ChainContext ctx) {
        log.info("后置处理：数据同步");
    }

    @ZestPostProcessor(value = "postCleanResource", name = "资源清理")
    public void postCleanResource(ChainContext ctx) {
        log.info("后置处理：资源清理");
    }

    @ZestPostProcessor(value = "postEventPublish", name = "事件发布")
    public void postEventPublish(ChainContext ctx) {
        log.info("后置处理：事件发布");
    }

    @ZestPostProcessor(value = "postCallbackInvoke", name = "回调调用")
    public void postCallbackInvoke(ChainContext ctx) {
        log.info("后置处理：回调调用");
    }
}
