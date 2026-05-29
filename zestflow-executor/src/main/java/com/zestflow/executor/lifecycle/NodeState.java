package com.zestflow.executor.lifecycle;

import java.util.Set;

/**
 * 节点状态枚举（对应 ChainConstants 中节点状态值）
 */
public enum NodeState {

    CREATED(0),
    READY(1),
    RUNNING(2),
    SUCCESS(3),
    FAILED(4),
    TIMEOUT(5),
    RETRYING(6),
    FALLBACKING(7),
    SKIPPED(8),
    COMPENSATED(9);

    private final int code;

    private static final NodeState[] VALUES = values();

    NodeState(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static NodeState fromCode(int code) {
        for (NodeState state : VALUES) {
            if (state.code == code) {
                return state;
            }
        }
        return CREATED;
    }

    /**
     * 合法状态转换表
     */
    private static final java.util.Map<NodeState, Set<NodeState>> TRANSITIONS = new java.util.HashMap<>();

    static {
        TRANSITIONS.put(CREATED, Set.of(READY, SKIPPED));
        TRANSITIONS.put(READY, Set.of(RUNNING, SKIPPED));
        TRANSITIONS.put(RUNNING, Set.of(SUCCESS, FAILED, TIMEOUT));
        TRANSITIONS.put(FAILED, Set.of(RETRYING, FALLBACKING, COMPENSATED));
        TRANSITIONS.put(TIMEOUT, Set.of(RETRYING, FALLBACKING, COMPENSATED));
        TRANSITIONS.put(RETRYING, Set.of(RUNNING, FALLBACKING));
        TRANSITIONS.put(FALLBACKING, Set.of(SUCCESS, FAILED));
        TRANSITIONS.put(SUCCESS, Set.of(COMPENSATED));
        TRANSITIONS.put(SKIPPED, Set.of());
        TRANSITIONS.put(COMPENSATED, Set.of());
    }

    /**
     * 判断是否可以转换到目标状态
     */
    public boolean canTransitTo(NodeState target) {
        Set<NodeState> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * 是否终止状态
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == COMPENSATED;
    }

    /**
     * 是否失败状态
     */
    public boolean isFailed() {
        return this == FAILED || this == TIMEOUT;
    }
}
