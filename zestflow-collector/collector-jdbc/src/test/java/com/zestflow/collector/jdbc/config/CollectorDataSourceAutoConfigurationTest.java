package com.zestflow.collector.jdbc.config;

import com.zestflow.collector.support.ZestFlowDataSourcePropertiesResolver;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectorDataSourceAutoConfigurationTest {

    @Test
    void mapperScan_bindsToCollectorSqlSessionFactory() {
        MapperScan scan = CollectorDataSourceAutoConfiguration.class.getAnnotation(MapperScan.class);
        assertThat(scan).isNotNull();
        assertThat(scan.sqlSessionFactoryRef()).isEqualTo("collectorSqlSessionFactory");
    }

    @Test
    void declaresIsolatedCollectorStack() throws NoSuchMethodException {
        assertThat(CollectorDataSourceAutoConfiguration.class.getDeclaredMethod(
                "collectorSqlSessionFactory", javax.sql.DataSource.class).getReturnType())
                .isEqualTo(SqlSessionFactory.class);
        assertThat(Arrays.stream(CollectorDataSourceAutoConfiguration.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.contains("mybatisPlusInterceptor") || name.contains("MetaObjectHandler")))
                .isTrue();
    }

    @Test
    void resolveDataSourceProperties_prefersCollectorUrl() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("zestflow.collector.datasource.url", "jdbc:mysql://log/db")
                .withProperty("spring.datasource.url", "jdbc:mysql://biz/db");

        assertThat(ZestFlowDataSourcePropertiesResolver.resolve(
                env, "zestflow.collector.datasource", "Collector").url())
                .isEqualTo("jdbc:mysql://log/db");
    }

    @Test
    void resolveDataSourceProperties_throwsWhenNoUrlConfigured() {
        assertThatThrownBy(() -> ZestFlowDataSourcePropertiesResolver.resolve(
                new MockEnvironment(), "zestflow.collector.datasource", "Collector"))
                .isInstanceOf(IllegalStateException.class);
    }
}
