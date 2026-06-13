package com.zestflow.executor.expression;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * 将 {@link ExecutorExpressionProperties} 应用到静态 {@link AviatorExpressionEvaluator}。
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnClass(AviatorExpressionEvaluator.class)
public class AviatorExpressionConfigurer {

    private final ExecutorExpressionProperties properties;

    @PostConstruct
    public void init() {
        AviatorExpressionEvaluator.configure(properties);
        log.info("Aviator 表达式引擎已初始化 timeoutMs={} maxScriptLength={} maxLoopCount={} maxCacheSize={}",
                properties.getTimeoutMs(),
                properties.getMaxScriptLength(),
                properties.getMaxLoopCount(),
                properties.getMaxCacheSize());
    }
}
