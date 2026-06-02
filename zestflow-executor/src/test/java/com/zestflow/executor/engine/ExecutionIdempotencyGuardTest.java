package com.zestflow.executor.engine;

import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionIdempotencyGuardTest {

    @Test
    void returnsCachedResultForSameKey() {
        ExecutionIdempotencyGuard guard = new ExecutionIdempotencyGuard();
        AtomicInteger calls = new AtomicInteger();

        ChainExecuteResultDTO first = guard.execute("key-1", 60_000, 5_000, () -> {
            calls.incrementAndGet();
            return result("inst-1");
        });
        ChainExecuteResultDTO second = guard.execute("key-1", 60_000, 5_000, () -> {
            calls.incrementAndGet();
            return result("inst-2");
        });

        assertThat(first.getInstanceId()).isEqualTo("inst-1");
        assertThat(second.getInstanceId()).isEqualTo("inst-1");
        assertThat(calls).hasValue(1);
    }

    @Test
    void concurrentRequestsShareSingleExecution() throws Exception {
        ExecutionIdempotencyGuard guard = new ExecutionIdempotencyGuard();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            var f1 = pool.submit(() -> guard.execute("dup", 60_000, 10_000, () -> {
                calls.incrementAndGet();
                started.countDown();
                sleepQuietly(200);
                return result("shared");
            }));
            started.await(2, TimeUnit.SECONDS);
            var f2 = pool.submit(() -> guard.execute("dup", 60_000, 10_000, () -> {
                calls.incrementAndGet();
                return result("other");
            }));

            assertThat(f1.get(5, TimeUnit.SECONDS).getInstanceId()).isEqualTo("shared");
            assertThat(f2.get(5, TimeUnit.SECONDS).getInstanceId()).isEqualTo("shared");
            assertThat(calls).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void blankKeyBypassesGuard() {
        ExecutionIdempotencyGuard guard = new ExecutionIdempotencyGuard();
        AtomicInteger calls = new AtomicInteger();

        guard.execute("  ", 60_000, 5_000, () -> {
            calls.incrementAndGet();
            return result("a");
        });
        guard.execute(null, 60_000, 5_000, () -> {
            calls.incrementAndGet();
            return result("b");
        });

        assertThat(calls).hasValue(2);
    }

    private static ChainExecuteResultDTO result(String instanceId) {
        return ChainExecuteResultDTO.builder()
                .instanceId(instanceId)
                .chainCode("chain-1")
                .build();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
