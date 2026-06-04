package com.zestflow.collector.jdbc.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zestflow.collector.support.ZestFlowDataSourcePropertiesResolver;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 采集器 JDBC 栈 — 命名隔离的多数据源装配（对标 Spring 官方多 {@code SqlSessionFactory} 方案）。
 * <p>
 * 始终自建 {@code collectorDataSource} + {@code collectorSqlSessionFactory}；同库/分库仅 JDBC URL 不同。
 * 不依赖 mybatis-plus-spring-boot3/4-starter，兼容 Spring Boot 3.x / 4.x 下游。
 */
@AutoConfiguration
@ConditionalOnClass({SqlSessionFactory.class, BaseMapper.class})
@MapperScan(basePackages = "com.zestflow.collector.jdbc.mapper", sqlSessionFactoryRef = "collectorSqlSessionFactory")
public class CollectorDataSourceAutoConfiguration {

    private static final String DATASOURCE_PREFIX = "zestflow.collector.datasource";

    @Bean(name = "collectorDataSource")
    HikariDataSource collectorDataSource(Environment env) {
        var settings = ZestFlowDataSourcePropertiesResolver.resolve(env, DATASOURCE_PREFIX, "Collector");
        return ZestFlowDataSourcePropertiesResolver.createHikariDataSource(settings);
    }

    @Bean(name = "collectorSqlSessionFactory")
    SqlSessionFactory collectorSqlSessionFactory(
            @Qualifier("collectorDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        CollectorMybatisPlusFactorySupport.configure(factory);
        return factory.getObject();
    }

    @Bean(name = "collectorTransactionManager")
    PlatformTransactionManager collectorTransactionManager(
            @Qualifier("collectorDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
