package com.zestflow.executor.config;

import com.zestflow.executor.chain.ChainDeclarationRegistry;
import com.zestflow.executor.chain.ExecutorChainProperties;
import com.zestflow.executor.http.ChainExecuteFacade;
import com.zestflow.executor.route.ChainRouteRegistry;
import org.springframework.beans.factory.ObjectProvider;
import com.zestflow.executor.server.ExecutorServer;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.zestflow.executor.server.NettyMvcDispatcher;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
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
    private final RequestMappingHandlerAdapter requestMappingHandlerAdapter;
    private final PlaygroundProperties playgroundProperties;
    private final ChainExecuteFacade chainExecuteFacade;
    private final ObjectProvider<ChainRouteRegistry> chainRouteRegistryProvider;
    private final ChainDeclarationRegistry chainDeclarationRegistry;
    private final ExecutorChainProperties chainProperties;

    public ExecutorEndpointConfig(ExecutorServer executorServer,
                                   RequestMappingHandlerMapping requestMappingHandlerMapping,
                                   RequestMappingHandlerAdapter requestMappingHandlerAdapter,
                                   PlaygroundProperties playgroundProperties,
                                   ChainExecuteFacade chainExecuteFacade,
                                   ObjectProvider<ChainRouteRegistry> chainRouteRegistryProvider,
                                   ChainDeclarationRegistry chainDeclarationRegistry,
                                   ExecutorChainProperties chainProperties) {
        this.executorServer = executorServer;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.requestMappingHandlerAdapter = requestMappingHandlerAdapter;
        this.playgroundProperties = playgroundProperties;
        this.chainExecuteFacade = chainExecuteFacade;
        this.chainRouteRegistryProvider = chainRouteRegistryProvider;
        this.chainDeclarationRegistry = chainDeclarationRegistry;
        this.chainProperties = chainProperties;
    }

    @PostConstruct
    public void wireEndpoints() {
        executorServer.setRequestMappingHandlerMapping(requestMappingHandlerMapping);
        executorServer.setScanPackages(playgroundProperties.getScanPackages());
        executorServer.setPlaygroundBusinessBaseUrl(playgroundProperties.getUrl());
        NettyMvcDispatcher dispatcher = new NettyMvcDispatcher(
                requestMappingHandlerMapping,
                requestMappingHandlerAdapter,
                playgroundProperties.getScanPackages(),
                chainRouteRegistryProvider.getIfAvailable(),
                chainExecuteFacade);
        executorServer.setNettyMvcDispatcher(dispatcher);
        executorServer.setChainExecuteFacade(chainExecuteFacade);
        executorServer.setChainDeclarationGuard(chainDeclarationRegistry, chainProperties);
    }
}
