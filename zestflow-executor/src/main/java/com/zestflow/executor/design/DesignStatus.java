package com.zestflow.executor.design;

/**
 * 设计启用状态（对应 zf_design.status）
 */
public final class DesignStatus {

    private DesignStatus() {}

    /** 停用 */
    public static final int DISABLED = 0;
    /** 启用 */
    public static final int ENABLED = 1;
}
