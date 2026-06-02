package com.zestflow.executor.engine;

import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 执行幂等守卫 — 相同 idempotencyKey 在 TTL 内返回同一结果，并发重复请求共享一次执行。
 */
@Slf4j
public class ExecutionIdempotencyGuard {

    private final ConcurrentHashMap<String, CompletableFuture<ChainExecuteResultDTO>> inFlight =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedResult> completed = new ConcurrentHashMap<>();

    public ChainExecuteResultDTO execute(String idempotencyKey, long ttlMs, long waitMs,
                                         Supplier<ChainExecuteResultDTO> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        evictExpired();
        CachedResult cached = completed.get(idempotencyKey);
        if (cached != null && !cached.expired()) {
            log.debug("幂等命中缓存 key={}", idempotencyKey);
            return cached.result();
        }

        CompletableFuture<ChainExecuteResultDTO> mine = new CompletableFuture<>();
        CompletableFuture<ChainExecuteResultDTO> existing = inFlight.putIfAbsent(idempotencyKey, mine);
        if (existing != null) {
            return awaitExisting(idempotencyKey, existing, waitMs);
        }

        try {
            ChainExecuteResultDTO result = action.get();
            mine.complete(result);
            completed.put(idempotencyKey, new CachedResult(result, System.currentTimeMillis() + ttlMs));
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } catch (Exception e) {
            mine.completeExceptionally(e);
            throw new RuntimeException(e);
        } finally {
            inFlight.remove(idempotencyKey, mine);
        }
    }

    private ChainExecuteResultDTO awaitExisting(String key, CompletableFuture<ChainExecuteResultDTO> existing,
                                                long waitMs) {
        try {
            log.debug("幂等等待在途执行 key={}", key);
            return existing.get(Math.max(1, waitMs), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("幂等等待被中断 key=" + key, e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("幂等等待超时 key=" + key, e);
        }
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        completed.entrySet().removeIf(entry -> entry.getValue().expiresAtMs() <= now);
    }

    private record CachedResult(ChainExecuteResultDTO result, long expiresAtMs) {
        boolean expired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }
    }
}
