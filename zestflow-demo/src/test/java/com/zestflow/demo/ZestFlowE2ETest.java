package com.zestflow.demo;

import com.zestflow.common.spi.EventCollector;
import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.*;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainDefinitionBuilder;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.ChainRuntimeRegistrar;
import com.zestflow.executor.engine.ChainExecutionEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ZestFlow E2E 全链路集成测试（12 场景）
 * <p>
 * 加载完整 Spring 上下文 + 真实 @ZestComponent 处理器，
 * 通过 ChainDefinitionBuilder → ChainManager → ChainExecutionEngine 执行全流程。
 * 事件通过 InMemoryEventCollector 同步验证。
 */
@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DemoApplication.class)
@ActiveProfiles("test")
@Import({ZestFlowE2ETest.InMemoryCollectorConfig.class})
class ZestFlowE2ETest {

    @Autowired
    private ChainExecutionEngine chainExecutionEngine;

    @Autowired
    private ChainManager chainManager;

    @Autowired
    private ChainDefinitionBuilder chainDefinitionBuilder;

    @Autowired
    private ChainRuntimeRegistrar chainRuntimeRegistrar;

    @Autowired
    private InMemoryEventCollector eventCollector;

    @Autowired
    @Qualifier("executorDataSource")
    private DataSource executorDataSource;

    @BeforeEach
    void setUp() {
        eventCollector.clear();
        // 每个测试方法使用唯一链编码，无需清除 ChainManager
    }

    // ==================== 1. 简单线性链 ====================

    @Test
    void simpleLinearChain() {
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("linear-test", List.of(
                node("A", "NORMAL", "validateUser"),
                node("B", "NORMAL", "processPayment"),
                node("C", "NORMAL", "deductStock")
        ), List.of(
                edge("A", "B"), edge("B", "C")
        ), Map.of("userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(3);
        assertThat(result.getNodeResults()).extracting(nr -> nr.getNodeId())
                .containsExactly("A", "B", "C");

        awaitEventType(ChainEvent.EventType.CHAIN_COMPLETED, 3_000);

        List<String> types = eventCollector.getEventTypes();
        assertThat(types).contains(
                ChainEvent.EventType.CHAIN_STARTED.name(),
                ChainEvent.EventType.CHAIN_COMPLETED.name(),
                ChainEvent.EventType.NODE_STARTED.name(),
                ChainEvent.EventType.NODE_COMPLETED.name()
        );
    }

    // ==================== 2. 并行 DAG 链 ====================

    @Test
    void parallelDagChain() {
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("parallel-test", List.of(
                node("A", "NORMAL", "validateUser"),
                node("B", "NORMAL", "processPayment"),
                node("C", "NORMAL", "deductStock"),
                node("D", "NORMAL", "sendNotify")
        ), List.of(
                edge("A", "B"), edge("A", "C"),
                edge("B", "D"), edge("C", "D")
        ), Map.of("userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(4);
    }

    // ==================== 3. 订单处理流程 ====================

    @Test
    void orderProcessingFlow() {
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("order-flow", List.of(
                node("create", "NORMAL", "createOrder"),
                node("pay", "NORMAL", "processPayment"),
                node("stock", "NORMAL", "deductStock"),
                node("done", "NORMAL", "sendNotify")
        ), List.of(
                edge("create", "pay"), edge("pay", "stock"), edge("stock", "done")
        ), Map.of("userId", "U1001", "productId", "SKU-001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(4);
        assertThat(eventCollector.getEventsByType(ChainEvent.EventType.NODE_COMPLETED)).hasSize(4);
    }

    // ==================== 4. 脚本节点执行 ====================

    @Test
    void scriptNodeExecution() {
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("script-test", List.of(
                node("start", "NORMAL", "validateUser"),
                ChainNodeDTO.builder().id("script1").label("script1")
                        .type("SCRIPT")
                        .script("ctx.put('msg', 'hello'); seq.map('greeting', 'hello')")
                        .build(),
                node("end", "NORMAL", "processPayment")
        ), List.of(
                edge("start", "script1"), edge("script1", "end")
        ), Map.of("userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(3);
    }

    // ==================== 5. 子链执行 ====================

    @Test
    void subChainExecution() {
        ChainDefinitionDTO subDTO = ChainDefinitionDTO.builder()
                .code("sub-chain-001")
                .nodes(List.of(
                        node("subA", "NORMAL", "printWaybill"),
                        node("subB", "NORMAL", "deliveryConfirm")
                ))
                .edges(List.of(edge("subA", "subB")))
                .build();
        chainManager.load(chainDefinitionBuilder.build(subDTO));
        chainRuntimeRegistrar.ensurePublished("sub-chain-001");

        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("main-chain", List.of(
                node("start", "NORMAL", "validateUser"),
                ChainNodeDTO.builder().id("sub1").label("sub1")
                        .type("SUB_CHAIN").subChainCode("sub-chain-001").build(),
                node("end", "NORMAL", "processPayment")
        ), List.of(
                edge("start", "sub1"), edge("sub1", "end")
        ), Map.of("userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(3);
    }

    // ==================== 6. 迭代器节点执行 ====================

    @Test
    void iteratorNodeExecution() {
        List<Map<String, Object>> items = List.of(
                Map.of("id", 1, "name", "a"),
                Map.of("id", 2, "name", "b"),
                Map.of("id", 3, "name", "c")
        );
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("iterator-test", List.of(
                node("start", "NORMAL", "validateUser"),
                ChainNodeDTO.builder().id("iter1").label("iter1")
                        .type("ITERATOR")
                        .config(Map.of(
                                "dataSource", "items",
                                "itemName", "currentItem",
                                "subNodes", List.of(
                                        Map.of("id", "subA", "label", "subA", "type", "NORMAL",
                                                "component", "noopStep")
                                )
                        )).build(),
                node("end", "NORMAL", "processPayment")
        ), List.of(
                edge("start", "iter1"), edge("iter1", "end")
        ), Map.of("items", items, "userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(3);
    }

    // ==================== 7. 条件分支（满足条件） ====================

    @Test
    void conditionBranchTrue() {
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("condition-true", List.of(
                node("start", "NORMAL", "validateUser"),
                ChainNodeDTO.builder().id("cond1").label("cond1")
                        .type("CONDITION")
                        .config(Map.of("condition", "params.status == 'PASS'")).build(),
                node("pass", "NORMAL", "processPayment"),
                node("end", "NORMAL", "deductStock")
        ), List.of(
                edge("start", "cond1"), edge("cond1", "pass"), edge("pass", "end")
        ), Map.of("status", "PASS", "userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).extracting(nr -> nr.getNodeId())
                .contains("cond1", "pass", "end");
    }

    // ==================== 7b. 内联脚本判断（Aviator）True 分支路由 ====================

    @Test
    void inlineScriptPredicateRoutesTrueBranch() {
        Map<String, Object> predCfg = Map.of(
                "predicateMode", "script",
                "predicateScript", "StringUtils.hasText(supplierType)",
                "trueLabel", "True",
                "falseLabel", "False"
        );
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("inline-pred-true", List.of(
                node("start", "NORMAL", "validateUser"),
                ChainNodeDTO.builder().id("cond").label("hasSupplier")
                        .type("CONDITION")
                        .component("INLINE_PRED_E2E")
                        .componentName("hasSupplier")
                        .config(predCfg)
                        .build(),
                node("pass", "NORMAL", "processPayment"),
                node("fail", "NORMAL", "deductStock"),
                node("end", "NORMAL", "sendNotify")
        ), List.of(
                edge("start", "cond"),
                edge("cond", "pass", "True"),
                edge("cond", "fail", "False"),
                edge("pass", "end"),
                edge("fail", "end")
        ), Map.of("supplierType", "OTA", "userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).extracting(nr -> nr.getNodeId())
                .containsExactly("start", "cond", "pass", "end");
    }

    // ==================== 7c. 内联脚本判断（Aviator）False 分支路由 ====================

    @Test
    void inlineScriptPredicateRoutesFalseBranch() {
        Map<String, Object> predCfg = Map.of(
                "predicateMode", "script",
                "predicateScript", "StringUtils.hasText(supplierType)",
                "trueLabel", "True",
                "falseLabel", "False"
        );
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("inline-pred-false", List.of(
                node("start", "NORMAL", "validateUser"),
                ChainNodeDTO.builder().id("cond").label("hasSupplier")
                        .type("CONDITION")
                        .component("INLINE_PRED_E2E")
                        .componentName("hasSupplier")
                        .config(predCfg)
                        .build(),
                node("pass", "NORMAL", "processPayment"),
                node("fail", "NORMAL", "deductStock"),
                node("end", "NORMAL", "sendNotify")
        ), List.of(
                edge("start", "cond"),
                edge("cond", "pass", "True"),
                edge("cond", "fail", "False"),
                edge("pass", "end"),
                edge("fail", "end")
        ), Map.of("userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).extracting(nr -> nr.getNodeId())
                .containsExactly("start", "cond", "fail", "end");
    }

    // ==================== 8. 重试 + 降级 ====================

    @Test
    void retryAndFallback() {
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("retry-test", List.of(
                node("start", "NORMAL", "validateUser"),
                ChainNodeDTO.builder().id("failing").label("failing")
                        .type("NORMAL").component("nonexistent_component")
                        .config(Map.of(
                                "retryCount", 2,
                                "fallback", Map.of("component", "payFallback"),
                                "circuitBreaker", Map.of("enabled", true, "failureThreshold", 5)
                        )).build()
        ), List.of(edge("start", "failing")), Map.of("userId", "U001"));

        // 重试耗尽后触发降级，降级成功则整个链成功
        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        awaitEventType(ChainEvent.EventType.NODE_FALLBACK_START, 3_000);
        assertThat(eventCollector.getEventsByType(ChainEvent.EventType.NODE_FALLBACK_START)).isNotEmpty();
    }

    // ==================== 9. 完整订单生命周期 ====================

    @Test
    void fullOrderLifecycle() {
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("full-lifecycle", List.of(
                node("create", "NORMAL", "createOrder"),
                node("pay", "NORMAL", "createPayment"),
                node("verify", "NORMAL", "verifySignature"),
                node("stock", "NORMAL", "checkStock"),
                node("deduct", "NORMAL", "deductStock"),
                node("notify", "NORMAL", "sendNotify"),
                node("split", "NORMAL", "splitAmount")
        ), List.of(
                edge("create", "pay"), edge("create", "verify"),
                edge("pay", "stock"), edge("verify", "stock"),
                edge("stock", "deduct"),
                edge("deduct", "notify"), edge("deduct", "split")
        ), Map.of("userId", "U999", "productId", "SKU-999", "quantity", 2));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(7);
        assertThat(eventCollector.getEventsByType(ChainEvent.EventType.CHAIN_STARTED)).hasSize(1);
        awaitEventType(ChainEvent.EventType.CHAIN_COMPLETED, 3_000);
        assertThat(eventCollector.getEventsByType(ChainEvent.EventType.CHAIN_COMPLETED)).hasSize(1);
    }

    // ==================== 10. 错误链条终止 ====================

    @Test
    void errorChainTermination() {
        com.zestflow.common.model.dto.ChainExecuteResultDTO result = execute("error-chain", List.of(
                node("A", "NORMAL", "validateUser"),
                node("B", "NORMAL", "nonexistent_component"),
                node("C", "NORMAL", "deductStock")
        ), List.of(
                edge("A", "B"), edge("B", "C")
        ), Map.of("userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        assertThat(result.getNodeResults()).hasSize(2);
        assertThat(result.getNodeResults().get(0).getNodeId()).isEqualTo("A");
        assertThat(result.getNodeResults().get(1).getNodeId()).isEqualTo("B");
        assertThat(result.getNodeResults().get(1).getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
    }

    // ==================== 辅助 ====================

    private com.zestflow.common.model.dto.ChainExecuteResultDTO execute(String code,
                                                                          List<ChainNodeDTO> nodes,
                                                                          List<ChainEdgeDTO> edges,
                                                                          Map<String, Object> params) {
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code(code).version(1).nodes(nodes).edges(edges)
                .config(Map.of("errorStrategy", ChainConstants.ERROR_STRATEGY_STOP))
                .build();
        chainManager.load(chainDefinitionBuilder.build(dto));
        chainRuntimeRegistrar.ensurePublished(code);
        log.info("执行链 code={} nodes={}", code, nodes.size());
        return chainExecutionEngine.execute(code, params != null ? params : Map.of());
    }

    private static ChainNodeDTO node(String id, String type, String component) {
        return ChainNodeDTO.builder().id(id).label(id).type(type).component(component).build();
    }

    private static ChainEdgeDTO edge(String source, String target) {
        return ChainEdgeDTO.builder().source(source).target(target).build();
    }

    private static ChainEdgeDTO edge(String source, String target, String label) {
        return ChainEdgeDTO.builder().source(source).target(target).label(label).build();
    }

    /** AsyncEventPublisher 批量刷盘前，轮询等待指定事件类型落盘 */
    private void awaitEventType(ChainEvent.EventType type, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!eventCollector.getEventsByType(type).isEmpty()) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ==================== InMemory EventCollector ====================

    public static class InMemoryEventCollector implements EventCollector {
        private final List<ChainEvent> events = Collections.synchronizedList(new ArrayList<>());

        @Override public void collect(ChainEvent event) { events.add(event); }
        @Override public void collectBatch(List<ChainEvent> batch) { events.addAll(batch); }
        public void clear() { events.clear(); }
        public List<String> getEventTypes() {
            synchronized (events) {
                return events.stream().map(e -> e.getEventType().name()).collect(Collectors.toList());
            }
        }
        public List<ChainEvent> getEventsByType(ChainEvent.EventType type) {
            synchronized (events) {
                return events.stream().filter(e -> e.getEventType() == type).collect(Collectors.toList());
            }
        }
    }

    @TestConfiguration
    public static class InMemoryCollectorConfig {
        @Bean
        @Primary
        public EventCollector eventCollector() {
            return new InMemoryEventCollector();
        }

        @Bean
        public InMemoryEventCollector inMemoryEventCollector(EventCollector eventCollector) {
            return (InMemoryEventCollector) eventCollector;
        }
    }
}
