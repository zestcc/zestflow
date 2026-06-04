package com.zestflow.collector.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
