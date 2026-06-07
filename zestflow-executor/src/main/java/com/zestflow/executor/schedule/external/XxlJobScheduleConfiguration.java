package com.zestflow.executor.schedule.external;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(XxlJobScheduleProperties.class)
@ConditionalOnProperty(prefix = "zestflow.executor.schedule", name = "driver", havingValue = "external")
public class XxlJobScheduleConfiguration {

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobScheduleProperties properties) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAccessToken(properties.getAccessToken());
        executor.setAppname(properties.getAppname());
        if (properties.getAddress() != null && !properties.getAddress().isBlank()) {
            executor.setAddress(properties.getAddress());
        }
        if (properties.getIp() != null && !properties.getIp().isBlank()) {
            executor.setIp(properties.getIp());
        }
        executor.setPort(properties.getPort());
        executor.setLogPath(properties.getLogPath());
        executor.setLogRetentionDays(properties.getLogRetentionDays());
        return executor;
    }

    @Bean
    public ExternalScheduleDriver externalScheduleDriver(XxlJobSpringExecutor xxlJobSpringExecutor,
                                                         XxlJobScheduleProperties properties) {
        return new ExternalScheduleDriver(xxlJobSpringExecutor, properties);
    }
}
