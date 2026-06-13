package com.zestflow.executor.schedule.routing;

import com.zestflow.common.model.dto.PeerExecutorDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RandomScheduleRouteStrategy implements ScheduleRouteStrategy {

    @Override
    public String name() {
        return "random";
    }

    @Override
    public PeerExecutorDTO select(List<PeerExecutorDTO> executors, String chainCode) {
        if (executors == null || executors.isEmpty()) {
            return null;
        }
        return executors.get(ThreadLocalRandom.current().nextInt(executors.size()));
    }
}
