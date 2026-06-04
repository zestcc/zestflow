package com.zestflow.collector.support;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 嵌入模式集成保护 — 与具体 transport 无关，引入 collector-core 即生效。
 */
@AutoConfiguration
public class ZestFlowHostIntegrationAutoConfiguration {

    @Bean
    static BeanFactoryPostProcessor zestFlowHostPrimaryBeanMarker() {
        return HostPrimaryBeanMarker.marker();
    }
}
