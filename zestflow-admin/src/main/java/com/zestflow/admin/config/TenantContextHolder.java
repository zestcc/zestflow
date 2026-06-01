package com.zestflow.admin.config;

/**
 * 租户上下文持有器 — ThreadLocal 模式
 * <p>
 * 在每个请求开始时由过滤器设置当前租户ID，请求结束时清理。
 * MyBatis-Plus TenantLineInnerInterceptor 从此处读取租户ID自动注入 SQL。
 */
public class TenantContextHolder {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    /**
     * 设置当前租户ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前租户ID，返回 null 表示不过滤
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 清理当前线程的租户ID（请求结束时调用）
     */
    public static void clear() {
        TENANT_ID.remove();
    }
}
