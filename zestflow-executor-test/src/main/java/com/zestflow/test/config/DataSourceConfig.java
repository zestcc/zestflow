package com.zestflow.test.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import lombok.Data;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 双数据源配置
 * <p>
 * 主数据源 → zestflow_test_bussiness（业务数据库）
 * 日志数据源 → zestflow_test_log（事件存储数据库）
 */
@Configuration
@MapperScan(basePackages = "com.zestflow.collector.jdbc.mapper", sqlSessionFactoryRef = "logSqlSessionFactory")
public class DataSourceConfig {

    @Primary
    @Bean(name = "businessDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource businessDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "logDataSource")
    @ConfigurationProperties(prefix = "zestflow.log.datasource")
    public DataSource logDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "businessSqlSessionFactory")
    public SqlSessionFactory businessSqlSessionFactory() throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(businessDataSource());
        return factory.getObject();
    }

    @Bean(name = "logSqlSessionFactory")
    public SqlSessionFactory logSqlSessionFactory() throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(logDataSource());
        return factory.getObject();
    }

    @Primary
    @Bean(name = "businessTransactionManager")
    public PlatformTransactionManager businessTransactionManager() {
        return new DataSourceTransactionManager(businessDataSource());
    }

    @Bean(name = "logTransactionManager")
    public PlatformTransactionManager logTransactionManager() {
        return new DataSourceTransactionManager(logDataSource());
    }
}
