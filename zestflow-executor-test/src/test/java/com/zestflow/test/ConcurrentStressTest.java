package com.zestflow.test;

import com.zestflow.common.model.Result;
import com.zestflow.test.dto.OrderRequest;
import com.zestflow.test.dto.PaymentRequest;
import com.zestflow.test.dto.InventoryRequest;
import com.zestflow.test.dto.WorkflowRequest;
import lombok.extern.slf4j.Slf4j;
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
 * 并发压测 — 多线程并发调用 Controller API
 * <p>
 * 验证：线程安全、无数据串扰、无死锁、无 OOM
 */
@Slf4j
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConcurrentStressTest {

    @Autowired
    private TestRestTemplate rest;

    private static final int THREAD_COUNT = 20;
    private static final int REQUESTS_PER_THREAD = 5;

    /**
     * 并发创建订单 + 支付 + 库存查询 — 混合场景
     */
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
                        // 每个请求交替调用不同 API
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

        boolean allDone = latch.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        double avgCost = costs.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxCost = costs.stream().mapToLong(Long::longValue).max().orElse(0);

        log.info("并发测试完成 total={} success={} fail={} avg={}ms max={}ms",
                total, successCount.get(), failCount.get(), String.format("%.1f", avgCost), maxCost);

        assertThat(allDone).as("所有请求应在 60s 内完成").isTrue();
        assertThat(successCount.get()).isEqualTo(total);
        assertThat(failCount.get()).isEqualTo(0);
    }

    /**
     * 同一链编码并发执行 — 验证重入安全
     */
    @Test
    void sameChainConcurrentExecution() throws Exception {
        String sharedCode = "stress-shared-chain-" + UUID.randomUUID().toString().substring(0, 8);
        int threads = 20;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            int idx = i;
            new Thread(() -> {
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
                    latch.countDown();
                }
            }).start();
        }

        boolean allDone = latch.await(30, TimeUnit.SECONDS);
        log.info("同链并发测试 threads={} success={} fail={}", threads, successCount.get(), failCount.get());
        assertThat(allDone).isTrue();
        assertThat(successCount.get()).isEqualTo(threads);
    }

    /**
     * 长链 50 节点 × 10 并发 — 验证长链无死锁
     */
    @Test
    void longChainConcurrent() throws Exception {
        int threads = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
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
                    latch.countDown();
                }
            }).start();
        }

        boolean allDone = latch.await(60, TimeUnit.SECONDS);
        log.info("长链并发测试 threads={} success={} fail={}", threads, successCount.get(), failCount.get());
        assertThat(allDone).isTrue();
        assertThat(successCount.get()).isEqualTo(threads);
    }

    /**
     * 迭代器大批量 × 5 并发 — 验证 ITERATOR 并发安全
     */
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

        boolean allDone = latch.await(60, TimeUnit.SECONDS);
        log.info("迭代器并发测试 threads={} success={}", threads, successCount.get());
        assertThat(allDone).isTrue();
        assertThat(successCount.get()).isEqualTo(threads);
    }
}
