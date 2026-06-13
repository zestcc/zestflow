package com.zestflow.executor.schedule.routing;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScheduleRouteStrategyRegistry {

    private final Map<String, ScheduleRouteStrategy> strategies;

    public ScheduleRouteStrategyRegistry(List<ScheduleRouteStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(ScheduleRouteStrategy::name, s -> s, (a, b) -> a));
    }

    public ScheduleRouteStrategy resolve(String routeStrategy) {
        if (routeStrategy == null || routeStrategy.isBlank() || "local".equalsIgnoreCase(routeStrategy)) {
            return null;
        }
        ScheduleRouteStrategy strategy = strategies.get(routeStrategy);
        return strategy != null ? strategy : strategies.get("round_robin");
    }
}
