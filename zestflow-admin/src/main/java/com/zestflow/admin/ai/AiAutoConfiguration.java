package com.zestflow.admin.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI Copilot 模块自动配置
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {
}
