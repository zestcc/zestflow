package com.zestflow.executor.server;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 链执行业务线程池（对标 xxl-job Executor 的业务线程池，避免阻塞 Netty EventLoop）。
 */
@Slf4j
public class ChainExecuteThreadPool {

    private final ThreadPoolExecutor executor;

    public ChainExecuteThreadPool(int coreSize, int maxSize, int queueCapacity) {
        int cores = Math.max(1, coreSize);
        int max = Math.max(cores, maxSize);
        int queue = Math.max(1, queueCapacity);
        AtomicInteger seq = new AtomicInteger(1);
        this.executor = new ThreadPoolExecutor(
                cores,
                max,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                r -> {
                    Thread t = new Thread(r, "zestflow-chain-exec-" + seq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("链执行线程池初始化 core={} max={} queue={}", cores, max, queue);
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("链执行线程池已关闭");
    }
}
