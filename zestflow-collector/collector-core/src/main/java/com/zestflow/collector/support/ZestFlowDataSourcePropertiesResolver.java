package com.zestflow.collector.support;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 数据源地址解析 — 专库优先，否则回落 {@code spring.datasource.*}（同库不同连接池）。
 * <p>
 * 不依赖 {@code DataSourceProperties} / Boot 3/4 自动配置类，保证跨 Spring Boot 版本兼容。
 * Executor / Collector 独立池统一应用 Hikari 保活与池大小默认值，避免裸池在小内存同机部署下放大 MySQL 抖动。
 */
public final class ZestFlowDataSourcePropertiesResolver {

    /** 单机小内存部署推荐：空闲 10 分钟回收，29 分钟强制换新（低于 MySQL wait_timeout） */
    private static final long DEFAULT_IDLE_TIMEOUT_MS = 600_000L;
    private static final long DEFAULT_MAX_LIFETIME_MS = 1_740_000L;
    private static final long DEFAULT_KEEPALIVE_TIME_MS = 120_000L;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 10_000L;
    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 10;
    private static final int DEFAULT_MINIMUM_IDLE = 2;

    private ZestFlowDataSourcePropertiesResolver() {
    }

    public record JdbcConnectionSettings(String url, String username, String password, String driverClassName) {
    }

    public static JdbcConnectionSettings resolve(Environment env, String dedicatedPrefix, String moduleName) {
        JdbcConnectionSettings dedicated = read(env, dedicatedPrefix);
        if (StringUtils.hasText(dedicated.url())) {
            return dedicated;
        }
        JdbcConnectionSettings primary = readWithJdbcUrlFallback(env, "spring.datasource");
        if (StringUtils.hasText(primary.url())) {
            return primary;
        }
        throw new IllegalStateException(
                "ZestFlow " + moduleName + " 需要 JDBC 数据源：请配置 "
                        + dedicatedPrefix + ".url，或确保 spring.datasource.url / jdbc-url 可用");
    }

    /**
     * 创建 Hikari 池（无 Environment 时仅设置 JDBC 凭据，供测试或极简场景）。
     */
    public static HikariDataSource createHikariDataSource(JdbcConnectionSettings settings) {
        return createHikariDataSource(null, settings, null, "ZestFlowPool");
    }

    /**
     * 创建 Hikari 池：读取 {@code {dedicatedPrefix}.hikari.*}，缺省回落 {@code spring.datasource.hikari.*}，再回落内置默认值。
     */
    public static HikariDataSource createHikariDataSource(
            Environment env, JdbcConnectionSettings settings, String dedicatedPrefix, String poolName) {
        HikariDataSource dataSource = new HikariDataSource();
        if (StringUtils.hasText(poolName)) {
            dataSource.setPoolName(poolName);
        }
        dataSource.setJdbcUrl(ensureTcpKeepAlive(settings.url()));
        if (settings.username() != null) {
            dataSource.setUsername(settings.username());
        }
        if (settings.password() != null) {
            dataSource.setPassword(settings.password());
        }
        if (StringUtils.hasText(settings.driverClassName())) {
            dataSource.setDriverClassName(settings.driverClassName());
        }
        applyHikariSettings(dataSource, env, dedicatedPrefix);
        return dataSource;
    }

    static String ensureTcpKeepAlive(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.startsWith("jdbc:mysql:")) {
            return jdbcUrl;
        }
        String lower = jdbcUrl.toLowerCase();
        if (lower.contains("tcpkeepalive=")) {
            return jdbcUrl;
        }
        char sep = jdbcUrl.contains("?") ? '&' : '?';
        return jdbcUrl + sep + "tcpKeepAlive=true";
    }

    private static void applyHikariSettings(HikariDataSource dataSource, Environment env, String dedicatedPrefix) {
        dataSource.setMaximumPoolSize(resolveInt(env, dedicatedPrefix, "maximum-pool-size", DEFAULT_MAXIMUM_POOL_SIZE));
        dataSource.setMinimumIdle(resolveInt(env, dedicatedPrefix, "minimum-idle", DEFAULT_MINIMUM_IDLE));
        dataSource.setConnectionTimeout(
                resolveLong(env, dedicatedPrefix, "connection-timeout", DEFAULT_CONNECTION_TIMEOUT_MS));
        dataSource.setIdleTimeout(resolveLong(env, dedicatedPrefix, "idle-timeout", DEFAULT_IDLE_TIMEOUT_MS));
        dataSource.setMaxLifetime(resolveLong(env, dedicatedPrefix, "max-lifetime", DEFAULT_MAX_LIFETIME_MS));
        dataSource.setKeepaliveTime(resolveLong(env, dedicatedPrefix, "keepalive-time", DEFAULT_KEEPALIVE_TIME_MS));
    }

    private static int resolveInt(Environment env, String dedicatedPrefix, String key, int defaultValue) {
        if (env == null) {
            return defaultValue;
        }
        Integer dedicated = env.getProperty(hikariKey(dedicatedPrefix, key), Integer.class);
        if (dedicated != null) {
            return dedicated;
        }
        Integer primary = env.getProperty(hikariKey("spring.datasource", key), Integer.class);
        return primary != null ? primary : defaultValue;
    }

    private static long resolveLong(Environment env, String dedicatedPrefix, String key, long defaultValue) {
        if (env == null) {
            return defaultValue;
        }
        Long dedicated = env.getProperty(hikariKey(dedicatedPrefix, key), Long.class);
        if (dedicated != null) {
            return dedicated;
        }
        Long primary = env.getProperty(hikariKey("spring.datasource", key), Long.class);
        return primary != null ? primary : defaultValue;
    }

    private static String hikariKey(String prefix, String key) {
        return prefix + ".hikari." + key;
    }

    private static JdbcConnectionSettings read(Environment env, String prefix) {
        return new JdbcConnectionSettings(
                env.getProperty(prefix + ".url"),
                env.getProperty(prefix + ".username"),
                env.getProperty(prefix + ".password"),
                env.getProperty(prefix + ".driver-class-name"));
    }

    /** Boot 2/3/4 部分项目使用 jdbc-url 而非 url */
    private static JdbcConnectionSettings readWithJdbcUrlFallback(Environment env, String prefix) {
        String url = env.getProperty(prefix + ".url");
        if (!StringUtils.hasText(url)) {
            url = env.getProperty(prefix + ".jdbc-url");
        }
        return new JdbcConnectionSettings(
                url,
                env.getProperty(prefix + ".username"),
                env.getProperty(prefix + ".password"),
                env.getProperty(prefix + ".driver-class-name"));
    }
}
