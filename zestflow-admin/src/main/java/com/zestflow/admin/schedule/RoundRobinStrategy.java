package com.zestflow.admin.schedule;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询策略 — 按请求顺序依次分配
 */
@Component
public class RoundRobinStrategy implements RouteStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String name() {
        return "round_robin";
    }

    @Override
    public ExecutorRegistryPO select(List<ExecutorRegistryPO> executors, String chainCode) {
        if (executors.isEmpty()) return null;
        int idx = Math.abs(counter.getAndIncrement()) % executors.size();
        return executors.get(idx);
    }
}
