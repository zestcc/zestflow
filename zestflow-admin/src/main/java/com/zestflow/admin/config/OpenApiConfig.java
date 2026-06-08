package com.zestflow.admin.config;

import com.zestflow.common.constant.AdminApiPaths;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Admin REST API OpenAPI 3 文档配置。
 * <p>
 * 运行时：{@code /v3/api-docs}、{@code /swagger-ui.html}（本地 profile 可开 UI）。<br>
 * 静态导出：{@code scripts/docs/export-openapi.ps1} → {@code docs/openapi/admin-api.json}
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_JWT = "bearer-jwt";
    private static final String SECURITY_REGISTRY = "registry-token";

    @Bean
    public OpenAPI zestFlowOpenApi(
            @Value("${spring.application.name:zestflow-admin}") String appName) {
        return new OpenAPI()
                .info(new Info()
                        .title("ZestFlow Admin API")
                        .description("""
                                ZestFlow 管理端 REST API（前缀 %s）。
                                
                                - 用户接口：JWT Bearer 认证
                                - 注册/心跳：X-Registry-Token（/registry/**）
                                - 链/设计/元件：多数代理至 Executor Netty
                                - 日志：聚合 Collector 查询
                                
                                人工维护说明见 docs/reference/API.md；本规范由 springdoc 从 Controller 自动生成。
                                """.formatted(AdminApiPaths.PREFIX))
                        .version("0.1.0")
                        .contact(new Contact().name("ZestFlow").url("https://www.zestflow.cn"))
                        .license(new License().name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addServersItem(new Server().url("/").description(appName))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_JWT))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录 POST %s/auth/login 获取 token"
                                        .formatted(AdminApiPaths.PREFIX)))
                        .addSecuritySchemes(SECURITY_REGISTRY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Registry-Token")
                                .description("Executor/Collector 机器接口令牌，与 zestflow.admin.registry-token 一致")));
    }

    @Bean
    public GroupedOpenApi adminRestApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("ZestFlow Admin REST")
                .pathsToMatch(AdminApiPaths.PREFIX + "/**")
                .build();
    }
}
