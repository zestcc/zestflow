package com.zestflow.executor.registry;

import com.zestflow.executor.server.ExecutorServer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

@AutoConfiguration
@EnableConfigurationProperties(ExecutorProperties.class)
public class ExecutorAutoConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ExecutorServer executorServer(ExecutorProperties properties) {
        return new ExecutorServer(properties.getPort());
    }

    @Bean
    public RestTemplate zestflowRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public AdminClient adminClient(RestTemplate restTemplate, ExecutorProperties properties) {
        return new AdminClient(restTemplate, properties);
    }

    @Bean
    public ExecutorRegistrar executorRegistrar(AdminClient adminClient,
                                               ExecutorProperties properties,
                                               ExecutorServer executorServer,
                                               Environment environment) {
        return new ExecutorRegistrar(adminClient, properties, executorServer, environment);
    }
}
