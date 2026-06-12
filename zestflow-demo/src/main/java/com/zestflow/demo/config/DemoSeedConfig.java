package com.zestflow.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LogAnalyticsSeedProperties.class)
public class DemoSeedConfig {
}
