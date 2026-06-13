package com.zestflow.admin.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 ZestSSO Client SDK（Back-Channel Logout 接收等），与现有 zestflow.sso SPI 并存。
 */
@Configuration
@ConditionalOnProperty(prefix = "zestflow.sso", name = "enabled", havingValue = "true")
public class ZestFlowSsoSdkConfiguration {
}
