package com.zestflow.admin.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 注册过期检测调度 — 心跳 touch 后延迟 {@code deadTimeout} 触发离线回调（事件驱动）。
 */
@Slf4j
@Component
public class RegistryExpiryScheduler {

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "registry-expiry");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, ScheduledFuture<?>> executorTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> collectorTasks = new ConcurrentHashMap<>();

    public void scheduleExecutorExpiryCheck(String executorId, long delayMs, Runnable onExpired) {
        reschedule(executorTasks, executorId, delayMs, onExpired);
    }

    public void scheduleCollectorExpiryCheck(String collectorId, long delayMs, Runnable onExpired) {
        reschedule(collectorTasks, collectorId, delayMs, onExpired);
    }

    public void cancelExecutor(String executorId) {
        cancel(executorTasks, executorId);
    }

    public void cancelCollector(String collectorId) {
        cancel(collectorTasks, collectorId);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    private void reschedule(Map<String, ScheduledFuture<?>> tasks, String id, long delayMs, Runnable onExpired) {
        ScheduledFuture<?> previous = tasks.get(id);
        if (previous != null && !previous.isDone()) {
            previous.cancel(false);
        }
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                onExpired.run();
            } catch (Exception e) {
                log.warn("注册过期回调失败 id={}", id, e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        tasks.put(id, future);
    }

    private static void cancel(Map<String, ScheduledFuture<?>> tasks, String id) {
        ScheduledFuture<?> previous = tasks.remove(id);
        if (previous != null) {
            previous.cancel(false);
        }
    }
}
