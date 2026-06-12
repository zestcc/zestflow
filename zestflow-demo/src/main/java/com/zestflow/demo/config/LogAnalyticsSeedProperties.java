package com.zestflow.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志分析报表演示数据种子配置（仅开发/演示环境使用）
 */
@Data
@ConfigurationProperties(prefix = "zestflow.demo.log-analytics-seed")
public class LogAnalyticsSeedProperties {

    /** 启动时自动灌数（库内已有足够数据则跳过） */
    private boolean enabled = false;

    /** 模拟执行次数（每次执行产生 5~15 条事件） */
    private int executions = 160;

    /** 保留进行中（未终结）的执行条数 */
    private int inProgress = 2;
}
