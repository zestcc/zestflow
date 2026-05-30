package com.zestflow.admin.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Collector 客户端配置 — 封装 Admin 连接 Collector 的参数
 */
@Configuration
@EnableConfigurationProperties(CollectorConfig.CollectorClientProperties.class)
public class CollectorConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // 默认 StringHttpMessageConverter 用 ISO-8859-1，中文会变 ????
        restTemplate.getMessageConverters().stream()
                .filter(c -> c instanceof org.springframework.http.converter.StringHttpMessageConverter)
                .forEach(c -> ((org.springframework.http.converter.StringHttpMessageConverter) c)
                        .setDefaultCharset(StandardCharsets.UTF_8));
        return restTemplate;
    }

    @Bean
    public CollectorClient collectorClient(RestTemplate restTemplate,
                                            CollectorClientProperties properties) {
        return new CollectorClient(restTemplate, properties.getApiUrl(), properties.getAccessToken());
    }

    @Data
    @ConfigurationProperties(prefix = "zestflow.collector")
    public static class CollectorClientProperties {
        /** Collector API 地址，如 http://localhost:8081 */
        private String apiUrl = "http://localhost:8081";

        /** Collector 认证令牌 */
        private String accessToken = "";
    }
}
