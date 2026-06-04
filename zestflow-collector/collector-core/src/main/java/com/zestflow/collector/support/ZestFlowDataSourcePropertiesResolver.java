package com.zestflow.collector.support;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 数据源地址解析 — 专库优先，否则回落 {@code spring.datasource.*}（同库不同连接池）。
 * <p>
 * 不依赖 {@code DataSourceProperties} / Boot 3/4 自动配置类，保证跨 Spring Boot 版本兼容。
 */
public final class ZestFlowDataSourcePropertiesResolver {

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

    public static HikariDataSource createHikariDataSource(JdbcConnectionSettings settings) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(settings.url());
        if (settings.username() != null) {
            dataSource.setUsername(settings.username());
        }
        if (settings.password() != null) {
            dataSource.setPassword(settings.password());
        }
        if (StringUtils.hasText(settings.driverClassName())) {
            dataSource.setDriverClassName(settings.driverClassName());
        }
        return dataSource;
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
