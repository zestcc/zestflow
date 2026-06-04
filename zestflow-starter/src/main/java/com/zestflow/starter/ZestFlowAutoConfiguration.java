package com.zestflow.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.PropertySource;

/**
 * ZestFlow 一键引入 — 仅聚合默认配置；各模块通过 {@code AutoConfiguration.imports} 自注册。
 */
@AutoConfiguration
@PropertySource("classpath:zestflow-starter-defaults.properties")
public class ZestFlowAutoConfiguration {
}
