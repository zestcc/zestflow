package com.zestflow.collector.support;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * 对标 Spring 多数据源惯例：ZestFlow 注册次要 Bean 后，将宿主业务 Bean 标为 {@code @Primary}，
 * 避免按类型注入歧义。
 */
public final class HostPrimaryBeanMarker {

    /** 宿主应用常见 Bean 名（MyBatis-Plus / Spring Boot JDBC 默认） */
    private static final String[] HOST_BEAN_NAMES = {
            "dataSource",
            "sqlSessionFactory",
            "sqlSessionTemplate",
            "transactionManager",
            "jdbcTemplate"
    };

    /** ZestFlow 注册的次要 Bean 名前缀/全名 */
    private static final String[] ZESTFLOW_SECONDARY_BEAN_NAMES = {
            "collectorDataSource",
            "executorDataSource",
            "collectorSqlSessionFactory",
            "collectorTransactionManager",
            "executorTransactionManager",
            "executorJdbcTemplate"
    };

    private HostPrimaryBeanMarker() {
    }

    public static BeanFactoryPostProcessor marker() {
        return (ConfigurableListableBeanFactory beanFactory) -> {
            if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
                return;
            }
            if (!hasZestFlowSecondaryBean(registry)) {
                return;
            }
            for (String hostBean : HOST_BEAN_NAMES) {
                if (registry.containsBeanDefinition(hostBean)) {
                    registry.getBeanDefinition(hostBean).setPrimary(true);
                }
            }
        };
    }

    private static boolean hasZestFlowSecondaryBean(BeanDefinitionRegistry registry) {
        for (String secondary : ZESTFLOW_SECONDARY_BEAN_NAMES) {
            if (registry.containsBeanDefinition(secondary)) {
                return true;
            }
        }
        return false;
    }
}
