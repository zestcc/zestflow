package com.zestflow.executor.schedule.routing;

import com.zestflow.common.model.dto.PeerExecutorDTO;

import java.util.List;

public class HashScheduleRouteStrategy implements ScheduleRouteStrategy {

    @Override
    public String name() {
        return "hash";
    }

    @Override
    public PeerExecutorDTO select(List<PeerExecutorDTO> executors, String chainCode) {
        if (executors == null || executors.isEmpty()) {
            return null;
        }
        String key = chainCode != null ? chainCode : "";
        int idx = Math.abs(key.hashCode()) % executors.size();
        return executors.get(idx);
    }
}
