package com.zestflow.admin.schedule;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteStrategyTest {

    private final List<ExecutorRegistryPO> executors = List.of(
            executor("e1", "192.168.1.1", 9999),
            executor("e2", "192.168.1.2", 9999),
            executor("e3", "192.168.1.3", 9999)
    );

    @Test
    void roundRobinRotates() {
        RoundRobinStrategy strategy = new RoundRobinStrategy();
        assertEquals("e1", strategy.select(executors, "chain1").getExecutorId());
        assertEquals("e2", strategy.select(executors, "chain1").getExecutorId());
        assertEquals("e3", strategy.select(executors, "chain1").getExecutorId());
        assertEquals("e1", strategy.select(executors, "chain1").getExecutorId());
    }

    @Test
    void hashConsistency() {
        HashStrategy strategy = new HashStrategy();
        ExecutorRegistryPO first = strategy.select(executors, "chain-order");
        for (int i = 0; i < 10; i++) {
            assertEquals(first.getExecutorId(),
                    strategy.select(executors, "chain-order").getExecutorId());
        }
    }

    @Test
    void randomReturnsNonNull() {
        RandomStrategy strategy = new RandomStrategy();
        for (int i = 0; i < 20; i++) {
            assertNotNull(strategy.select(executors, "chain1"));
        }
    }

    @Test
    void emptyListReturnsNull() {
        List<ExecutorRegistryPO> empty = List.of();
        assertEquals(null, new RoundRobinStrategy().select(empty, "chain1"));
        assertEquals(null, new HashStrategy().select(empty, "chain1"));
        assertEquals(null, new RandomStrategy().select(empty, "chain1"));
    }

    private ExecutorRegistryPO executor(String id, String host, int port) {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setExecutorId(id);
        po.setExecutorHost(host);
        po.setExecutorPort(port);
        return po;
    }
}
