package com.zestflow.demo.config;

import com.zestflow.demo.service.LogAnalyticsMockSeeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时可选自动灌入日志分析演示数据
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "zestflow.demo.log-analytics-seed", name = "enabled", havingValue = "true")
public class LogAnalyticsSeedRunner implements ApplicationRunner {

    private final LogAnalyticsMockSeeder seeder;

    @Override
    public void run(ApplicationArguments args) {
        LogAnalyticsMockSeeder.SeedResult result = seeder.seed(false);
        log.info("[log-analytics-seed] 自动灌数结果: {}", result);
    }
}
