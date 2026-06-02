package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
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
 * 包含执行上下文、状态机、绝对 deadline 等，用于追踪单次链执行的生命周期。
 */
public class ChainInstance {

    /** 无父链 deadline 约束时的占位值 */
    public static final long NO_PARENT_DEADLINE = Long.MAX_VALUE;

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

    /** 绝对 deadline 时间戳（毫秒）；{@link Long#MAX_VALUE} 表示无上限 */
    @Getter
    private final long deadlineMs;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public ChainInstance(ChainDefinition chainDefinition, Map<String, Object> params) {
        this(chainDefinition, params, NO_PARENT_DEADLINE);
    }

    public ChainInstance(ChainDefinition chainDefinition, Map<String, Object> params, long parentDeadlineMs) {
        this.instanceId = UUID.randomUUID().toString().replace("-", "");
        this.chainCode = chainDefinition.getCode();
        this.chainDefinition = chainDefinition;
        this.startTime = System.currentTimeMillis();
        this.deadlineMs = resolveDeadline(chainDefinition, parentDeadlineMs, startTime);
        this.context = new ChainContext(this.instanceId, chainDefinition.getCode(), params);
        this.context.setMetadata(ChainConstants.META_DEADLINE_MS, deadlineMs);
        this.stateMachine = new ChainStateMachine();
    }

    static long resolveDeadline(ChainDefinition definition, long parentDeadlineMs, long startTime) {
        long chainEnd = definition.getTimeout() > 0
                ? startTime + definition.getTimeout()
                : NO_PARENT_DEADLINE;
        if (parentDeadlineMs <= 0 || parentDeadlineMs >= NO_PARENT_DEADLINE) {
            return chainEnd;
        }
        if (chainEnd >= NO_PARENT_DEADLINE) {
            return parentDeadlineMs;
        }
        return Math.min(chainEnd, parentDeadlineMs);
    }

    public boolean isStopped() {
        return stopped.get();
    }

    public void markStopped() {
        stopped.set(true);
    }

    public long elapsed() {
        return System.currentTimeMillis() - startTime;
    }

    public boolean hasDeadline() {
        return deadlineMs < NO_PARENT_DEADLINE;
    }

    public boolean isTimedOut() {
        return hasDeadline() && System.currentTimeMillis() >= deadlineMs;
    }

    /** 距离 deadline 的剩余毫秒；无 deadline 时返回 {@link Long#MAX_VALUE} */
    public long getRemainingMs() {
        if (!hasDeadline()) {
            return NO_PARENT_DEADLINE;
        }
        return Math.max(0, deadlineMs - System.currentTimeMillis());
    }
}
