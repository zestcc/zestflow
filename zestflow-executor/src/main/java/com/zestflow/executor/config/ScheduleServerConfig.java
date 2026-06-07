package com.zestflow.executor.config;

import com.zestflow.executor.schedule.ScheduleRouteHandler;
import com.zestflow.executor.server.ExecutorServer;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScheduleServerConfig {

    private final ExecutorServer executorServer;
    private final ScheduleRouteHandler scheduleRouteHandler;

    public ScheduleServerConfig(ExecutorServer executorServer, ScheduleRouteHandler scheduleRouteHandler) {
        this.executorServer = executorServer;
        this.scheduleRouteHandler = scheduleRouteHandler;
    }

    @PostConstruct
    public void wireScheduleRoutes() {
        executorServer.setScheduleRouteHandler(scheduleRouteHandler);
    }
}
