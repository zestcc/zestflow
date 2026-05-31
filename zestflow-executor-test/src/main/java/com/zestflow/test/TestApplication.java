package com.zestflow.test;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@Slf4j
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onStarted() {
        log.info("========================================");
        log.info("测试执行器已启动，正在注册到 Admin...");
        log.info("spring.application.name=test-executor（自动作为 module-code）");
        log.info("zestflow.executor.admin-addresses=http://localhost:8080");
        log.info("========================================");
    }
}
