package com.zestflow.executor.chain;

/**
 * 链生命周期状态（对应 zf_chain.status）
 */
public final class ChainLifecycleStatus {

    private ChainLifecycleStatus() {}

    /** 停用 */
    public static final int DISABLED = 0;
    /** 设计中（流程未通过校验） */
    public static final int DESIGNING = 1;
    /** 未发布（流程校验通过，可发布） */
    public static final int UNPUBLISHED = 2;
    /** 发布中 */
    public static final int PUBLISHING = 3;
    /** 已发布 */
    public static final int PUBLISHED = 4;
}
