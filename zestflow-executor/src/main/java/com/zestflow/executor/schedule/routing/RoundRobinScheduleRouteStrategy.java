package com.zestflow.executor.schedule.routing;

import com.zestflow.common.model.dto.PeerExecutorDTO;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinScheduleRouteStrategy implements ScheduleRouteStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String name() {
        return "round_robin";
    }

    @Override
    public PeerExecutorDTO select(List<PeerExecutorDTO> executors, String chainCode) {
        if (executors == null || executors.isEmpty()) {
            return null;
        }
        int idx = Math.abs(counter.getAndIncrement()) % executors.size();
        return executors.get(idx);
    }
}
