package com.zestflow.admin.schedule;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机策略 — 从在线执行器中随机选取
 */
@Component
public class RandomStrategy implements RouteStrategy {

    @Override
    public String name() {
        return "random";
    }

    @Override
    public ExecutorRegistryPO select(List<ExecutorRegistryPO> executors, String chainCode) {
        if (executors.isEmpty()) return null;
        return executors.get(ThreadLocalRandom.current().nextInt(executors.size()));
    }
}
