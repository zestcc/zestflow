package com.zestflow.collector.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HostPrimaryBeanMarkerTest {

    @Test
    void marksAllHostMyBatisBeansPrimaryWhenCollectorPresent() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("sqlSessionFactory", new RootBeanDefinition(Object.class));
        factory.registerBeanDefinition("sqlSessionTemplate", new RootBeanDefinition(Object.class));
        factory.registerBeanDefinition("transactionManager", new RootBeanDefinition(Object.class));
        factory.registerBeanDefinition("collectorSqlSessionFactory", new RootBeanDefinition(Object.class));

        HostPrimaryBeanMarker.marker().postProcessBeanFactory(factory);

        assertTrue(factory.getBeanDefinition("sqlSessionFactory").isPrimary());
        assertTrue(factory.getBeanDefinition("sqlSessionTemplate").isPrimary());
        assertTrue(factory.getBeanDefinition("transactionManager").isPrimary());
    }
}
