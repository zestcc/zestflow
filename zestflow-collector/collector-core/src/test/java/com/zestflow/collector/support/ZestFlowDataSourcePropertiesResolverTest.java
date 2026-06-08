package com.zestflow.collector.support;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZestFlowDataSourcePropertiesResolverTest {

    @Test
    void resolve_prefersDedicatedPrefix() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("zestflow.collector.datasource.url", "jdbc:mysql://log/db")
                .withProperty("spring.datasource.url", "jdbc:mysql://biz/db");

        var settings = ZestFlowDataSourcePropertiesResolver.resolve(
                env, "zestflow.collector.datasource", "Collector");
        assertEquals("jdbc:mysql://log/db", settings.url());
    }

    @Test
    void resolve_fallsBackToSpringDatasource() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://biz/db")
                .withProperty("spring.datasource.username", "biz");

        var settings = ZestFlowDataSourcePropertiesResolver.resolve(
                env, "zestflow.collector.datasource", "Collector");
        assertEquals("jdbc:mysql://biz/db", settings.url());
        assertEquals("biz", settings.username());
    }

    @Test
    void resolve_throwsWhenNoUrlConfigured() {
        assertThrows(IllegalStateException.class, () -> ZestFlowDataSourcePropertiesResolver.resolve(
                new MockEnvironment(), "zestflow.collector.datasource", "Collector"));
    }

    @Test
    void ensureTcpKeepAlive_appendsForMysqlUrl() {
        String url = ZestFlowDataSourcePropertiesResolver.ensureTcpKeepAlive(
                "jdbc:mysql://127.0.0.1:3306/db?useSSL=false");
        assertTrue(url.contains("tcpKeepAlive=true"));
    }

    @Test
    void createHikariDataSource_appliesDefaultsAndDedicatedHikariOverride() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("zestflow.executor.datasource.url", "jdbc:mysql://127.0.0.1:3306/ex")
                .withProperty("zestflow.executor.datasource.hikari.maximum-pool-size", "6")
                .withProperty("spring.datasource.hikari.idle-timeout", "30000");

        var settings = ZestFlowDataSourcePropertiesResolver.resolve(
                env, "zestflow.executor.datasource", "Executor");
        HikariDataSource ds = ZestFlowDataSourcePropertiesResolver.createHikariDataSource(
                env, settings, "zestflow.executor.datasource", "TestPool");

        assertEquals("TestPool", ds.getPoolName());
        assertEquals(6, ds.getMaximumPoolSize());
        assertEquals(30_000L, ds.getIdleTimeout());
        assertEquals(1_740_000L, ds.getMaxLifetime());
        assertEquals(120_000L, ds.getKeepaliveTime());
        assertTrue(ds.getJdbcUrl().contains("tcpKeepAlive=true"));
        ds.close();
    }
}
