package com.zestflow.executor.expression;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Aviator 表达式引擎配置 — 绑定 {@code zestflow.executor.expression.*}。
 */
@Data
@ConfigurationProperties(prefix = "zestflow.executor.expression")
public class ExecutorExpressionProperties {

    /** 单次条件求值 / SCRIPT 脚本执行超时（毫秒） */
    private long timeoutMs = 5_000L;

    /** 表达式最大字符数 */
    private int maxScriptLength = 10_000;

    /** 编译缓存最大条目（LRU 淘汰） */
    private int maxCacheSize = 1_000;

    /** Aviator 循环上限（防止 busy-loop 占满 CPU） */
    private int maxLoopCount = 10_000;

    /** 条件表达式求值失败时是否视为 true（默认 false = fail-closed，走 false 分支） */
    private boolean conditionFailOpen = false;

    /** 链热加载成功后是否清理表达式编译缓存 */
    private boolean clearCacheOnChainReload = true;
}
