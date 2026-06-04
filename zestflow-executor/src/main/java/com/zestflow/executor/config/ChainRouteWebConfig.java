package com.zestflow.executor.config;

import com.zestflow.executor.route.ChainRouteHandlerMapping;
import com.zestflow.executor.route.ChainRouteRegistry;
import com.zestflow.executor.http.ChainExecuteFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

/**
 * Mode 2 链路由 WebMvc 配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "zestflow.executor", name = "chain-route-enabled", havingValue = "true")
public class ChainRouteWebConfig {

    @Bean
    public ChainRouteRegistry chainRouteRegistry() {
        return new ChainRouteRegistry();
    }

    @Bean
    public AbstractHandlerMapping chainRouteHandlerMapping(ChainRouteRegistry registry,
                                                             ChainExecuteFacade facade) {
        return new ChainRouteHandlerMapping(registry, facade);
    }
}
