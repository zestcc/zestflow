package com.zestflow.demo.service;

import com.zestflow.collector.spi.EventQueryService;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.demo.config.LogAnalyticsSeedProperties;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 日志分析报表演示数据生成器 — 通过 EventCollector 写入 chain_event + execution_payload
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalyticsMockSeeder {

    private static final long TENANT_ID = 1L;
    private static final int BATCH_SIZE = 400;
    private static final int SKIP_THRESHOLD = 20;

    private static final List<String> ERROR_MESSAGES = List.of(
            "库存不足: SKU-10023 可用库存为0",
            "支付网关超时: connection timed out after 3000ms",
            "风控拦截: 用户风险等级过高",
            "参数校验失败: amount 不能为负数",
            "数据库连接池耗尽: Unable to acquire JDBC Connection"
    );

    private static final List<MockChain> CHAINS = List.of(
            new MockChain("CHN_DEMO_ORDER_CREATE", "订单创建",
                    List.of(node("validateOrder", "校验订单"), node("createOrder", "创建订单"), node("notifyUser", "通知用户"))),
            new MockChain("CHN_DEMO_PAYMENT_RISK", "支付风控",
                    List.of(node("checkRisk", "风控检查"), node("processPayment", "处理支付"))),
            new MockChain("CHN_DEMO_ORDER_PIPELINE", "订单流水线",
                    List.of(node("reserveStock", "预占库存"), node("createOrder", "创建订单"), node("sendMq", "发送消息"))),
            new MockChain("CHN_DEMO_MARKETING_BRANCH", "营销分支",
                    List.of(node("loadUser", "加载用户"), node("sendCoupon", "发放优惠券"))),
            new MockChain("CHN_DEMO_STRESS_75", "压测链",
                    List.of(node("step1", "步骤一"), node("step2", "步骤二"), node("step3", "步骤三")))
    );

    private final EventCollector eventCollector;
    private final EventQueryService eventQueryService;
    private final ExecutorProperties executorProperties;
    private final LogAnalyticsSeedProperties seedProperties;

    /**
     * 灌入演示数据；若近期已有足够执行记录且非强制，则跳过。
     *
     * @param force 为 true 时忽略已有数据检查
     * @return 摘要信息
     */
    public SeedResult seed(boolean force) {
        if (!force && hasEnoughExistingData()) {
            log.info("[log-analytics-seed] 已有足够演示数据，跳过");
            return new SeedResult(0, 0, true, "skipped: enough data exists");
        }
        return doSeed();
    }

    public boolean hasEnoughExistingData() {
        long now = System.currentTimeMillis();
        EventStats stats = eventQueryService.queryStats(EventStatsQuery.builder()
                .tenantId(TENANT_ID)
                .appCode(resolveAppCode())
                .startTime(now - 30L * 24 * 3600_000)
                .endTime(now)
                .build());
        return stats != null && stats.getExecutionCount() >= SKIP_THRESHOLD;
    }

    private SeedResult doSeed() {
        String appCode = resolveAppCode();
        String appName = executorProperties.getAppName();
        List<String> executors = List.of(
                appCode + "@127.0.0.1:20550",
                appCode + "@192.168.1.101:20550",
                appCode + "@192.168.1.102:20550"
        );

        int totalExecutions = seedProperties.getExecutions();
        int inProgressCount = seedProperties.getInProgress();
        long now = System.currentTimeMillis();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        List<ChainEvent> buffer = new ArrayList<>();
        int eventCount = 0;

        for (int i = 0; i < totalExecutions; i++) {
            MockChain chain = CHAINS.get(rnd.nextInt(CHAINS.size()));
            String executorId = executors.get(rnd.nextInt(executors.size()));
            long startTs = randomStartTimestamp(now, rnd);
            boolean inProgress = i < inProgressCount;
            boolean success = !inProgress && rnd.nextInt(100) < 88;
            int failNodeIdx = (!inProgress && !success) ? rnd.nextInt(chain.nodes().size()) : -1;
            String errorMsg = failNodeIdx >= 0 ? ERROR_MESSAGES.get(rnd.nextInt(ERROR_MESSAGES.size())) : null;
            long totalCost = randomTotalCost(rnd);

            String executionId = UUID.randomUUID().toString().replace("-", "");
            buffer.add(chainEvent(executionId, ChainEvent.EventType.CHAIN_STARTED, chain, null,
                    executorId, appCode, appName, startTs, null, null, null, null));
            eventCount++;

            long cursor = startTs;
            long accumulated = 0;
            for (int n = 0; n < chain.nodes().size(); n++) {
                MockNode node = chain.nodes().get(n);
                long nodeCost = randomNodeCost(rnd, n == chain.nodes().size() - 1 ? totalCost - accumulated : -1);
                accumulated += nodeCost;
                cursor += rnd.nextInt(5, 20);

                buffer.add(chainEvent(executionId, ChainEvent.EventType.NODE_STARTED, chain, node,
                        executorId, appCode, appName, cursor, null, null, null, null));
                eventCount++;
                cursor += nodeCost;

                if (failNodeIdx >= 0 && n == failNodeIdx) {
                    buffer.add(chainEvent(executionId, ChainEvent.EventType.NODE_FAILED, chain, node,
                            executorId, appCode, appName, cursor, nodeCost, 0, errorMsg,
                            "{\"orderId\":\"ORD-MOCK-" + i + "\"}"));
                    eventCount++;
                    buffer.add(chainEvent(executionId, ChainEvent.EventType.CHAIN_FAILED, chain, null,
                            executorId, appCode, appName, cursor + rnd.nextInt(5, 15), accumulated, 0, errorMsg,
                            "{\"orderId\":\"ORD-MOCK-" + i + "\"}"));
                    eventCount++;
                    break;
                }

                buffer.add(chainEvent(executionId, ChainEvent.EventType.NODE_COMPLETED, chain, node,
                        executorId, appCode, appName, cursor, nodeCost, 1, null,
                        "{\"node\":\"" + node.id() + "\"}"));
                eventCount++;

                if (inProgress && n >= Math.max(0, chain.nodes().size() / 2)) {
                    break;
                }
            }

            if (success && !inProgress) {
                long endTs = startTs + totalCost;
                buffer.add(chainEvent(executionId, ChainEvent.EventType.CHAIN_COMPLETED, chain, null,
                        executorId, appCode, appName, endTs, totalCost, 1, null,
                        "{\"status\":\"OK\"}"));
                eventCount++;
            }

            if (buffer.size() >= BATCH_SIZE) {
                eventCollector.collectBatch(buffer);
                buffer.clear();
            }
        }

        if (!buffer.isEmpty()) {
            eventCollector.collectBatch(buffer);
        }

        log.info("[log-analytics-seed] 完成 executions={} events={} appCode={}", totalExecutions, eventCount, appCode);
        return new SeedResult(totalExecutions, eventCount, false, "seeded");
    }

    private String resolveAppCode() {
        return executorProperties.getAppCode();
    }

    private static long randomStartTimestamp(long now, ThreadLocalRandom rnd) {
        int bucket = rnd.nextInt(100);
        long offsetMs;
        if (bucket < 60) {
            offsetMs = rnd.nextLong(1, 24L * 3600_000);
        } else if (bucket < 85) {
            offsetMs = rnd.nextLong(24L * 3600_000, 7L * 24 * 3600_000);
        } else {
            offsetMs = rnd.nextLong(7L * 24 * 3600_000, 30L * 24 * 3600_000);
        }
        long hourBias = rnd.nextInt(100) < 70 ? rnd.nextInt(9, 19) : rnd.nextInt(24);
        long ts = now - offsetMs;
        ts = (ts / 86_400_000L) * 86_400_000L + hourBias * 3_600_000L + rnd.nextInt(3_600_000);
        return Math.min(ts, now - 60_000);
    }

    private static long randomTotalCost(ThreadLocalRandom rnd) {
        int roll = rnd.nextInt(100);
        if (roll < 5) {
            return rnd.nextLong(5_000, 8_000);
        }
        if (roll < 20) {
            return rnd.nextLong(1_200, 3_500);
        }
        return rnd.nextLong(80, 600);
    }

    private static long randomNodeCost(ThreadLocalRandom rnd, long remaining) {
        if (remaining > 0) {
            return Math.max(20, remaining);
        }
        return rnd.nextLong(30, 250);
    }

    private static ChainEvent chainEvent(String executionId, ChainEvent.EventType type, MockChain chain,
                                         MockNode node, String executorId, String appCode, String appName,
                                         long timestamp, Long costMs, Integer status, String errorMessage,
                                         String params) {
        return ChainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(type)
                .executionId(executionId)
                .chainId(chain.code())
                .chainName(chain.name())
                .nodeId(node != null ? node.id() : null)
                .nodeName(node != null ? node.name() : null)
                .executorId(executorId)
                .appCode(appCode)
                .appName(appName)
                .tenantId(TENANT_ID)
                .timestamp(timestamp)
                .costMs(costMs)
                .status(status)
                .errorMessage(errorMessage)
                .params(params)
                .result(status != null && status == 1 ? "{\"ok\":true}" : null)
                .build();
    }

    private static MockNode node(String id, String name) {
        return new MockNode(id, name);
    }

    private record MockChain(String code, String name, List<MockNode> nodes) {
    }

    private record MockNode(String id, String name) {
    }

    public record SeedResult(int executions, int events, boolean skipped, String message) {
    }
}
