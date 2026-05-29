package com.zestflow.executor.engine;

import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.lifecycle.ChainStateMachine;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 运行中的链实例
 * <p>
 * 包含执行上下文、状态机、开始时间等，用于追踪单次链执行的生命周期。
 */
public class ChainInstance {

    @Getter
    private final String instanceId;

    @Getter
    private final String chainCode;

    @Getter
    private final ChainDefinition chainDefinition;

    @Getter
    private final ChainContext context;

    @Getter
    private final ChainStateMachine stateMachine;

    @Getter
    private final long startTime;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public ChainInstance(ChainDefinition chainDefinition, Map<String, Object> params) {
        this.instanceId = UUID.randomUUID().toString().replace("-", "");
        this.chainCode = chainDefinition.getCode();
        this.chainDefinition = chainDefinition;
        this.context = new ChainContext(this.instanceId, chainDefinition.getCode(), params);
        this.stateMachine = new ChainStateMachine();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 是否已被外部终止
     */
    public boolean isStopped() {
        return stopped.get();
    }

    /**
     * 标记终止
     */
    public void markStopped() {
        stopped.set(true);
    }

    /**
     * 获取已执行时间（毫秒）
     */
    public long elapsed() {
        return System.currentTimeMillis() - startTime;
    }
}
