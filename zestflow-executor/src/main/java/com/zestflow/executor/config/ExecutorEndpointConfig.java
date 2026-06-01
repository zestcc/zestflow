package com.zestflow.executor.config;

import com.zestflow.executor.server.ExecutorServer;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 端点扫描配置 — 仅在 spring-webmvc 可用时激活
 * <p>
 * 将 RequestMappingHandlerMapping 注入到 ExecutorServer，
 * 使得 Netty 的 /api/endpoints 可以扫描当前应用的 Spring MVC 控制器端点。
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping")
@EnableConfigurationProperties(PlaygroundProperties.class)
public class ExecutorEndpointConfig {

    private final ExecutorServer executorServer;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final PlaygroundProperties playgroundProperties;

    public ExecutorEndpointConfig(ExecutorServer executorServer,
                                   RequestMappingHandlerMapping requestMappingHandlerMapping,
                                   PlaygroundProperties playgroundProperties) {
        this.executorServer = executorServer;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.playgroundProperties = playgroundProperties;
    }

    @PostConstruct
    public void wireEndpoints() {
        executorServer.setRequestMappingHandlerMapping(requestMappingHandlerMapping);
        executorServer.setScanPackages(playgroundProperties.getScanPackages());
        executorServer.setPlaygroundUrl(playgroundProperties.getUrl());
    }
}
