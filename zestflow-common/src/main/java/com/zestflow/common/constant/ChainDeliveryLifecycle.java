package com.zestflow.common.constant;

/**
 * 链交付生命周期 — 与 {@link ChainLifecycleStatus}（设计/发布状态）独立。
 * <ul>
 *   <li>{@link #BOOTSTRAP} — 声明占位 / Seeder 单节点，validate_delivery 可跳过</li>
 *   <li>{@link #PRODUCTION} — MCP compose 或达标设计，validate_delivery 必须 pass</li>
 * </ul>
 */
public final class ChainDeliveryLifecycle {

    public static final String BOOTSTRAP = "bootstrap";
    public static final String PRODUCTION = "production";

    private ChainDeliveryLifecycle() {
    }

    public static boolean isProduction(String lifecycle) {
        return PRODUCTION.equalsIgnoreCase(lifecycle);
    }

    public static boolean isBootstrap(String lifecycle) {
        return lifecycle == null || lifecycle.isBlank() || BOOTSTRAP.equalsIgnoreCase(lifecycle);
    }
}
