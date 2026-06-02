package com.zestflow.admin.client;

import com.zestflow.admin.service.CollectorRegistryService;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Collector 客户端配置 — 封装 Admin 连接 Collector 的参数
 */
@Configuration
@EnableConfigurationProperties(CollectorConfig.CollectorClientProperties.class)
public class CollectorConfig {

    @Bean
    public RestTemplate restTemplate(@Value("${zestflow.admin.http-timeout-ms:5000}") int timeoutMs) {
        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        restTemplate.setRequestFactory(factory);
        // 默认 StringHttpMessageConverter 用 ISO-8859-1，中文会变 ????
        restTemplate.getMessageConverters().stream()
                .filter(c -> c instanceof org.springframework.http.converter.StringHttpMessageConverter)
                .forEach(c -> ((org.springframework.http.converter.StringHttpMessageConverter) c)
                        .setDefaultCharset(StandardCharsets.UTF_8));
        return restTemplate;
    }

    @Bean
    public CollectorClient collectorClient(RestTemplate restTemplate,
                                            CollectorClientProperties properties,
                                            CollectorRegistryService registryService) {
        return new CollectorClient(restTemplate, properties.getAccessToken(), registryService, properties);
    }

    @Bean
    public CollectorQueryClient collectorQueryClient(RestTemplate restTemplate,
                                                      CollectorClientProperties properties) {
        return new CollectorQueryClient(restTemplate, properties.getAccessToken());
    }

    @Data
    @ConfigurationProperties(prefix = "zestflow.collector")
    public static class CollectorClientProperties {
        /** Collector API 地址（可选），为空则从注册表查找在线采集器 */
        private String apiUrl = "";

        /** Collector 认证令牌 */
        private String accessToken = "";
    }
}
