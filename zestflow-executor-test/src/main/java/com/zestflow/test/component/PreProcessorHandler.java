package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestPreProcessor;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 前置处理器测试
 */
@Slf4j
@ZestComponent("preprocessor")
public class PreProcessorHandler {

    @ZestPreProcessor(value = "preValidateParams", name = "参数校验")
    public void preValidateParams(ChainContext ctx) {
        log.info("前置处理：参数校验");
    }

    @ZestPreProcessor(value = "preCheckPermission", name = "权限检查")
    public void preCheckPermission(ChainContext ctx) {
        log.info("前置处理：权限检查");
    }

    @ZestPreProcessor(value = "preLoadConfig", name = "配置加载")
    public void preLoadConfig(ChainContext ctx) {
        log.info("前置处理：配置加载");
    }

    @ZestPreProcessor(value = "preInitData", name = "数据初始化")
    public void preInitData(ChainContext ctx) {
        log.info("前置处理：数据初始化");
    }

    @ZestPreProcessor(value = "preRequestLog", name = "请求日志")
    public void preRequestLog(ChainContext ctx) {
        log.info("前置处理：请求日志");
    }

    @ZestPreProcessor(value = "preRateLimit", name = "限流检查")
    public void preRateLimit(ChainContext ctx) {
        log.info("前置处理：限流检查");
    }

    @ZestPreProcessor(value = "preBlacklistCheck", name = "黑名单检查")
    public void preBlacklistCheck(ChainContext ctx) {
        log.info("前置处理：黑名单检查");
    }

    @ZestPreProcessor(value = "preEncryptData", name = "数据解密")
    public void preEncryptData(ChainContext ctx) {
        log.info("前置处理：数据解密");
    }

    @ZestPreProcessor(value = "preBuildContext", name = "上下文构建")
    public void preBuildContext(ChainContext ctx) {
        log.info("前置处理：上下文构建");
    }

    @ZestPreProcessor(value = "preIdempotentCheck", name = "幂等检查")
    public void preIdempotentCheck(ChainContext ctx) {
        log.info("前置处理：幂等检查");
    }
}
