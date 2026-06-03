package com.zestflow.collector.jdbc.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 采集器数据源自动装配
 * <p>
 * 支持两种模式：
 * <ol>
 *   <li>配置 zestflow.collector.datasource.url → 创建独立数据源（事件独库）</li>
 *   <li>未配置 → 复用主数据源（事件与业务共库）</li>
 * </ol>
 * 仅在 MyBatis-Plus 在类路径时生效（collector-jdbc 声明为 optional）。
 */
@Configuration
@ConditionalOnClass({SqlSessionFactory.class, BaseMapper.class})
@MapperScan(basePackages = "com.zestflow.collector.jdbc.mapper", sqlSessionFactoryRef = "collectorSqlSessionFactory")
public class CollectorDataSourceConfig {

    // ==================== 独立数据源（可选） ====================

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.collector.datasource", name = "url")
    public HikariDataSource collectorDataSource(Environment env) {
        DataSourceProperties props = Binder.get(env)
                .bind("zestflow.collector.datasource", DataSourceProperties.class)
                .orElseGet(DataSourceProperties::new);
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    // ==================== 复用主数据源（兜底） ====================

    @Bean("collectorDataSource")
    @ConditionalOnMissingBean(name = "collectorDataSource")
    public DataSource primaryDataSourceFallback(@Qualifier("dataSource") DataSource primaryDataSource) {
        return primaryDataSource;
    }

    // ==================== MyBatis session factory ====================

    @Bean
    public SqlSessionFactory collectorSqlSessionFactory(
            @Qualifier("collectorDataSource") DataSource ds) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate collectorSqlSessionTemplate(
            @Qualifier("collectorSqlSessionFactory") SqlSessionFactory sf) {
        return new SqlSessionTemplate(sf);
    }

    @Bean
    public PlatformTransactionManager collectorTransactionManager(
            @Qualifier("collectorDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
