package com.zestflow.admin.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StandaloneScheduleMonitorDeployModeTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestSchedulingConfiguration.class)
            .withBean(ScheduleScanService.class, () -> mock(ScheduleScanService.class))
            .withUserConfiguration(TestSchedulingConfiguration.class, StandaloneScheduleMonitor.class);

    @Test
    void loadsStandaloneMonitorInStandaloneMode() {
        contextRunner
                .withPropertyValues("zestflow.admin.deploy-mode=standalone")
                .run(context -> assertThat(context).hasSingleBean(StandaloneScheduleMonitor.class));
    }

    @Test
    void skipsStandaloneMonitorInClusterMode() {
        contextRunner
                .withPropertyValues("zestflow.admin.deploy-mode=cluster")
                .run(context -> assertThat(context).doesNotHaveBean(StandaloneScheduleMonitor.class));
    }

    @Configuration
    @EnableScheduling
    static class TestSchedulingConfiguration {
    }
}
