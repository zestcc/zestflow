package com.zestflow.executor.lifecycle;

import java.util.Set;

/**
 * 链状态枚举（对应 ChainConstants 中链状态值）
 */
public enum ChainState {

    INIT(0),
    LOADING(1),
    READY(2),
    RUNNING(3),
    SUCCESS(4),
    FAILED(5),
    TIMEOUT(6),
    COMPENSATED(7),
    STOPPED(8);

    private final int code;

    private static final ChainState[] VALUES = values();

    ChainState(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ChainState fromCode(int code) {
        for (ChainState state : VALUES) {
            if (state.code == code) {
                return state;
            }
        }
        return INIT;
    }

    /**
     * 合法状态转换表
     */
    private static final java.util.Map<ChainState, Set<ChainState>> TRANSITIONS = new java.util.HashMap<>();

    static {
        TRANSITIONS.put(INIT, Set.of(LOADING, READY));
        TRANSITIONS.put(LOADING, Set.of(READY, FAILED));
        TRANSITIONS.put(READY, Set.of(RUNNING, INIT, FAILED));
        TRANSITIONS.put(RUNNING, Set.of(SUCCESS, FAILED, TIMEOUT, STOPPED, COMPENSATED));
        TRANSITIONS.put(SUCCESS, Set.of(INIT));
        TRANSITIONS.put(FAILED, Set.of(COMPENSATED, INIT));
        TRANSITIONS.put(TIMEOUT, Set.of(FAILED));
        TRANSITIONS.put(COMPENSATED, Set.of(READY));
        TRANSITIONS.put(STOPPED, Set.of(INIT));
    }

    /**
     * 判断是否可以转换到目标状态
     */
    public boolean canTransitTo(ChainState target) {
        Set<ChainState> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * 是否终止状态
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT
                || this == COMPENSATED || this == STOPPED;
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return this == RUNNING;
    }
}
