package com.zestflow.demo;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.*;
import com.zestflow.executor.chain.ChainDefinitionBuilder;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.ChainRuntimeRegistrar;
import com.zestflow.executor.engine.ChainExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 元件强类型参数与参数绑定器 E2E — 业务方法可不声明 ChainContext，靠 @ZestParam / POJO 返回值 / @ZestParamBinder。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DemoApplication.class)
@ActiveProfiles("test")
class ComponentParamBindingE2ETest {

    @Autowired
    private ChainExecutionEngine chainExecutionEngine;

    @Autowired
    private ChainManager chainManager;

    @Autowired
    private ChainDefinitionBuilder chainDefinitionBuilder;

    @Autowired
    private ChainRuntimeRegistrar chainRuntimeRegistrar;

    @BeforeEach
    void setUp() {
        // 每个用例使用唯一链编码，避免 ChainManager 冲突
    }

    @Test
    void typedParamPipeline_mapReturnPropagatesToDownstream() {
        ChainExecuteResultDTO result = execute("typed-pipeline-" + System.nanoTime(), List.of(
                node("A", "echoUser"),
                node("B", "scaleAmount"),
                node("C", "consumeScaled")
        ), List.of(edge("A", "B"), edge("B", "C")),
                Map.of("userId", "U-TYPED", "amount", 10));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(3);
        Map<String, Object> lastOut = result.getNodeResults().get(2).getOutputData();
        assertThat(lastOut.get("consumed")).isEqualTo(20);
        assertThat(lastOut.get("echoUserId")).isEqualTo("U-TYPED");
        assertThat(lastOut.get("scaledAmount")).isEqualTo(20);
    }

    @Test
    void validateUser_usesZestParamNotChainContext() {
        ChainExecuteResultDTO result = execute("typed-validate-" + System.nanoTime(), List.of(
                node("A", "validateUser"),
                node("B", "sendNotify")
        ), List.of(edge("A", "B")), Map.of("userId", "U001", "orderId", "ORD-1"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        Map<String, Object> notifyOut = result.getNodeResults().get(1).getOutputData();
        assertThat(notifyOut.get("sent")).isEqualTo(true);
        assertThat(notifyOut.get("userId")).isEqualTo("U001");
    }

    @Test
    void missingRequiredParam_failsNode() {
        ChainExecuteResultDTO result = execute("typed-missing-" + System.nanoTime(), List.of(
                node("A", "echoUser")
        ), List.of(), Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        assertThat(result.getNodeResults()).hasSize(1);
        assertThat(result.getNodeResults().get(0).getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
    }

    @Test
    void defaultValueParam_whenKeyAbsent() {
        ChainExecuteResultDTO result = execute("typed-default-" + System.nanoTime(), List.of(
                node("A", "greetName")
        ), List.of(), Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults().get(0).getOutputData()).isNotNull();
    }

    @Test
    void calcCashback_pojoInAndOut_notContextOrMap() {
        ChainExecuteResultDTO result = execute("cashback-pojo-" + System.nanoTime(), List.of(
                node("A", "calcCashback"),
                node("B", "redeemPoints")
        ), List.of(edge("A", "B")), Map.of("amount", 300.0));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        Map<String, Object> cashbackOut = result.getNodeResults().get(0).getOutputData();
        assertThat(cashbackOut.get("cashbackAmount")).isEqualTo(15.0);
        assertThat(cashbackOut.get("rule")).isEqualTo("ORDER_AMOUNT_5%");
        Map<String, Object> pointsOut = result.getNodeResults().get(1).getOutputData();
        assertThat(pointsOut.get("pointsUsed")).isEqualTo(1500);
        assertThat(pointsOut.get("reward")).isEqualTo("满100减10券");
    }

    @Test
    void paramBinder_preComponentEnrichesContextBeforeExecute() {
        ChainExecuteResultDTO result = execute("typed-binder-" + System.nanoTime(), List.of(
                nodeWithPre("read", "readBoundOrder", List.of("bindOrderParam"))
        ), List.of(), Map.of("rawOrderId", "RAW-99", "rawAmount", "199.5"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        Map<String, Object> out = result.getNodeResults().get(0).getOutputData();
        assertThat(out.get("orderId")).isEqualTo("RAW-99");
        assertThat(out.get("userId")).isEqualTo("U-BIND");
    }

    private ChainExecuteResultDTO execute(String code,
                                          List<ChainNodeDTO> nodes,
                                          List<ChainEdgeDTO> edges,
                                          Map<String, Object> params) {
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code(code).version(1).nodes(nodes).edges(edges)
                .config(Map.of("errorStrategy", ChainConstants.ERROR_STRATEGY_STOP))
                .build();
        chainManager.load(chainDefinitionBuilder.build(dto));
        chainRuntimeRegistrar.ensurePublished(code);
        return chainExecutionEngine.execute(code, params != null ? params : Map.of());
    }

    private static ChainNodeDTO node(String id, String component) {
        return ChainNodeDTO.builder()
                .id(id).label(id).type(ChainConstants.NODE_TYPE_NORMAL).component(component)
                .build();
    }

    private static ChainNodeDTO nodeWithPre(String id, String component, List<String> preIds) {
        return ChainNodeDTO.builder()
                .id(id).label(id).type(ChainConstants.NODE_TYPE_NORMAL).component(component)
                .preComponents(preIds.stream()
                        .map(pid -> ComponentRef.builder().componentId(pid).build())
                        .toList())
                .build();
    }

    private static ChainEdgeDTO edge(String source, String target) {
        return ChainEdgeDTO.builder().source(source).target(target).build();
    }
}
