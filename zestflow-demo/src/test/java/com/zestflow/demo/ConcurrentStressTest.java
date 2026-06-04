package com.zestflow.demo;

import com.zestflow.common.model.Result;
import com.zestflow.common.util.LatencyPercentiles;
import com.zestflow.demo.dto.InventoryRequest;
import com.zestflow.demo.dto.OrderRequest;
import com.zestflow.demo.dto.PaymentRequest;
import com.zestflow.demo.dto.WorkflowRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2c — 并发压测 + P99.9 延迟门禁（HTTP 全栈，含 Spring + 引擎 + 业务元件）。
 */
@Slf4j
@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("perf")
class ConcurrentStressTest {

    private static final int THREAD_COUNT = 20;
    private static final int REQUESTS_PER_THREAD = 5;

    /** diamond 20 并发 P99.9 上限（ms） */
    private static final long DIAMOND_P999_LIMIT_MS = 5_000L;
    /** long-50 × 10 并发 P99.9 上限（ms） */
    private static final long LONG50_P999_LIMIT_MS = 60_000L;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void mixedConcurrentScenario() throws Exception {
        int total = THREAD_COUNT * REQUESTS_PER_THREAD;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Long> costs = new ConcurrentLinkedQueue<>();
        CountDownLatch latch = new CountDownLatch(total);

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            int threadIdx = i;
            for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                int reqIdx = j;
                pool.submit(() -> {
                    long start = System.currentTimeMillis();
                    try {
                        boolean isOrder = (threadIdx + reqIdx) % 3 == 0;
                        boolean isPayment = (threadIdx + reqIdx) % 3 == 1;
                        if (isOrder) {
                            var req = OrderRequest.builder()
                                    .userId("U-CONC-" + threadIdx)
                                    .productId("PROD-" + reqIdx)
                                    .quantity(1).amount(50).build();
                            ResponseEntity<Result> resp = rest.postForEntity(
                                    "/api/orders/create", req, Result.class);
                            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null
                                    && resp.getBody().getCode() == 200) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                        } else if (isPayment) {
                            var req = PaymentRequest.builder()
                                    .orderId("ORD-CS-" + threadIdx + "-" + reqIdx)
                                    .userId("U-CONC-" + threadIdx).amount(30).build();
                            ResponseEntity<Result> resp = rest.postForEntity(
                                    "/api/payments/quick", req, Result.class);
                            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null
                                    && resp.getBody().getCode() == 200) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                        } else {
                            var req = WorkflowRequest.builder()
                                    .scenario("diamond").build();
                            ResponseEntity<Result> resp = rest.postForEntity(
                                    "/api/workflow/diamond", req, Result.class);
                            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null
                                    && resp.getBody().getCode() == 200) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        log.error("请求异常 thread={} req={}", threadIdx, reqIdx, e);
                    } finally {
                        costs.add(System.currentTimeMillis() - start);
                        latch.countDown();
                    }
                });
            }
        }

        assertThat(latch.await(60, TimeUnit.SECONDS)).as("所有请求应在 60s 内完成").isTrue();
        pool.shutdown();

        LatencyPercentiles stats = LatencyPercentiles.fromMillis(costs);
        log.info("[perf-gate] mixedConcurrent {} success={} fail={}",
                stats, successCount.get(), failCount.get());

        assertThat(successCount.get()).isEqualTo(total);
        assertThat(failCount.get()).isZero();
    }

    @Test
    void sameChainConcurrentExecution() throws Exception {
        int threads = 20;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Long> costs = new ConcurrentLinkedQueue<>();
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            int idx = i;
            new Thread(() -> {
                long start = System.currentTimeMillis();
                try {
                    var req = WorkflowRequest.builder().scenario("diamond").build();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<WorkflowRequest> entity = new HttpEntity<>(req, headers);

                    ResponseEntity<Result<Map>> resp = rest.exchange(
                            "/api/workflow/diamond", HttpMethod.POST, entity,
                            new ParameterizedTypeReference<Result<Map>>() {});
                    if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null
                            && resp.getBody().getCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("同链并发异常 thread={}", idx, e);
                } finally {
                    costs.add(System.currentTimeMillis() - start);
                    latch.countDown();
                }
            }).start();
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

        LatencyPercentiles stats = LatencyPercentiles.fromMillis(costs);
        log.info("[perf-gate] diamond×{} {} success={} fail={}",
                threads, stats, successCount.get(), failCount.get());

        assertThat(successCount.get()).isEqualTo(threads);
        assertThat(failCount.get()).isZero();
        assertThat(stats.p999Ms())
                .as("diamond 20 并发 P99.9")
                .isLessThanOrEqualTo(DIAMOND_P999_LIMIT_MS);
    }

    @Test
    void longChainConcurrent() throws Exception {
        int threads = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Long> costs = new ConcurrentLinkedQueue<>();
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                long start = System.currentTimeMillis();
                try {
                    var req = WorkflowRequest.builder().scenario("long-50").build();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<WorkflowRequest> entity = new HttpEntity<>(req, headers);

                    ResponseEntity<Result<Map>> resp = rest.exchange(
                            "/api/workflow/long-50", HttpMethod.POST, entity,
                            new ParameterizedTypeReference<Result<Map>>() {});
                    if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null
                            && resp.getBody().getCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    costs.add(System.currentTimeMillis() - start);
                    latch.countDown();
                }
            }).start();
        }

        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();

        LatencyPercentiles stats = LatencyPercentiles.fromMillis(costs);
        log.info("[perf-gate] long-50×{} {} success={} fail={}",
                threads, stats, successCount.get(), failCount.get());

        assertThat(successCount.get()).isEqualTo(threads);
        assertThat(failCount.get()).isZero();
        assertThat(stats.p999Ms())
                .as("long-50 10 并发 P99.9")
                .isLessThanOrEqualTo(LONG50_P999_LIMIT_MS);
    }

    @Test
    void iteratorConcurrent() throws Exception {
        int threads = 5;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            int idx = i;
            new Thread(() -> {
                try {
                    var req = InventoryRequest.builder()
                            .sku("STRESS-" + idx).quantity(50).operation("BATCH_IMPORT").build();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<InventoryRequest> entity = new HttpEntity<>(req, headers);

                    ResponseEntity<Result<Map>> resp = rest.exchange(
                            "/api/inventory/batch-import", HttpMethod.POST, entity,
                            new ParameterizedTypeReference<Result<Map>>() {});
                    if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null
                            && resp.getBody().getCode() == 200) {
                        Map data = (Map) resp.getBody().getData();
                        if ("SUCCESS".equals(data.get("status"))) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    log.error("迭代器并发异常 thread={}", idx, e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
        log.info("[perf-gate] iterator×{} success={}", threads, successCount.get());
        assertThat(successCount.get()).isEqualTo(threads);
    }
}
