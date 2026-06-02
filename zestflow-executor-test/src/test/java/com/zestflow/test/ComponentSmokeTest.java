package com.zestflow.test;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.ComponentType;
import com.zestflow.common.model.dto.*;
import com.zestflow.executor.chain.ChainDefinitionBuilder;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.engine.ChainExecutionEngine;
import com.zestflow.executor.lifecycle.LifecycleExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import com.zestflow.executor.scanner.ComponentScanner.ComponentMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全量元件冒烟：扫描 {@link ComponentScanner} 注册表，逐个调用，确保强类型改造后均可正常执行。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComponentSmokeTest {

    private static final Set<String> SKIP_IDS = Set.of(
            "zestParamResolver",
            "contextTypeResolver"
    );

    /** 需多节点前置上下文的元件 */
    private static final Map<String, List<String>> MULTI_NODE_CHAINS = Map.of(
            "redeemPoints", List.of("calcCashback", "redeemPoints"),
            "consumeScaled", List.of("scaleAmount", "consumeScaled")
    );

    /** 故意失败元件（验证异常路径，链/节点应失败但不崩溃） */
    private static final Set<String> EXPECT_FAIL_IDS = Set.of("failStep");

    @Autowired
    private ComponentScanner componentScanner;
    @Autowired
    private ChainExecutionEngine chainExecutionEngine;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private ChainDefinitionBuilder chainDefinitionBuilder;
    @Autowired
    private LifecycleExecutor lifecycleExecutor;

    @Test
    void registryShouldContainAllDemoComponents() {
        int count = componentScanner.componentCount();
        assertThat(count)
                .as("演示应用 + 引擎内置元件应 >= 112")
                .isGreaterThanOrEqualTo(112);
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("registeredComponents")
    void smokeComponent(String componentId, ComponentType type) {
        Map<String, Object> params = smokeParams();
        if (EXPECT_FAIL_IDS.contains(componentId)) {
            ChainExecuteResultDTO result = runChain(componentId, type, params, componentId);
            assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
            assertThat(result.getNodeResults()).isNotEmpty();
            assertThat(result.getNodeResults().get(0).getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
            return;
        }

        if ("defaultParamValidator".equals(componentId)) {
            ChainExecuteResultDTO result = runChain("validateUser", ComponentType.EXECUTOR, params,
                    "defaultParamValidator");
            assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
            return;
        }

        if (isDirectInvokeType(type)) {
            smokeDirectInvoke(componentId, params);
            return;
        }

        ChainExecuteResultDTO result = runChain(componentId, type, params, componentId);
        assertThat(result.getStatus())
                .as("元件 %s 冒烟应成功", componentId)
                .isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).isNotEmpty();
        NodeResultDTO lastNode = result.getNodeResults().get(result.getNodeResults().size() - 1);
        assertThat(lastNode.getStatus())
                .as("元件 %s 末节点应成功", componentId)
                .isEqualTo(ChainConstants.NODE_SUCCESS);
    }

    Stream<org.junit.jupiter.params.provider.Arguments> registeredComponents() {
        return componentScanner.getComponentIds().stream()
                .sorted()
                .filter(id -> !SKIP_IDS.contains(id))
                .map(id -> {
                    ComponentMeta meta = componentScanner.getComponent(id);
                    return org.junit.jupiter.params.provider.Arguments.of(id, meta.getComponentType());
                });
    }

    private ChainExecuteResultDTO runChain(String componentId, ComponentType type,
                                           Map<String, Object> params, String chainSuffix) {
        List<String> sequence = MULTI_NODE_CHAINS.getOrDefault(componentId, List.of(componentId));
        List<ChainNodeDTO> nodes = new ArrayList<>();
        List<ChainEdgeDTO> edges = new ArrayList<>();
        for (int i = 0; i < sequence.size(); i++) {
            String comp = sequence.get(i);
            String nodeId = "n" + i;
            ComponentType compType = componentScanner.getComponent(comp).getComponentType();
            nodes.add(buildNode(nodeId, comp, compType));
            if (i > 0) {
                edges.add(ChainEdgeDTO.builder().source("n" + (i - 1)).target(nodeId).build());
            }
        }
        String chainCode = "smoke-" + chainSuffix + "-" + System.nanoTime();
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code(chainCode).version(1).nodes(nodes).edges(edges)
                .config(Map.of("errorStrategy", ChainConstants.ERROR_STRATEGY_STOP))
                .build();
        chainManager.load(chainDefinitionBuilder.build(dto));
        return chainExecutionEngine.execute(chainCode, params);
    }

    private ChainNodeDTO buildNode(String nodeId, String componentId, ComponentType type) {
        String nodeType = (type == ComponentType.PREDICATE || type == ComponentType.SELECTOR)
                ? ChainConstants.NODE_TYPE_CONDITION
                : ChainConstants.NODE_TYPE_NORMAL;
        return ChainNodeDTO.builder()
                .id(nodeId).label(nodeId).type(nodeType).component(componentId)
                .build();
    }

    private void smokeDirectInvoke(String componentId, Map<String, Object> params) {
        ComponentMeta meta = componentScanner.getComponent(componentId);
        assertThat(meta).isNotNull();
        ChainContext ctx = new ChainContext("smoke-inst", "smoke-chain", params);
        Method method = meta.getExecuteMethod();

        if (meta.getComponentType() == ComponentType.PARAM_VALIDATOR) {
            invokeParamValidator(meta, ctx, method);
            return;
        }

        lifecycleExecutor.invokeMethod(
                method, meta.getTargetBean(), ctx, null,
                defaultResolverRefs(), "defaultParamValidator");
    }

    private static List<ComponentRef> defaultResolverRefs() {
        return List.of(
                ComponentRef.builder().componentId("zestParamResolver").build(),
                ComponentRef.builder().componentId("contextTypeResolver").build()
        );
    }

    private void invokeParamValidator(ComponentMeta meta, ChainContext ctx, Method method) {
        try {
            Object[] execArgs = new Object[]{ctx};
            Parameter[] execParams = new Parameter[0];
            method.invoke(meta.getTargetBean(), execArgs, execParams);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new AssertionError("参数校验器冒烟失败: " + meta.getExecuteId() + ": " + cause.getMessage(), cause);
        }
    }

    private static boolean isDirectInvokeType(ComponentType type) {
        return type == ComponentType.PRE_PROCESSOR
                || type == ComponentType.POST_PROCESSOR
                || type == ComponentType.PARAM_BINDER
                || type == ComponentType.PARAM_VALIDATOR;
    }

    private static Map<String, Object> smokeParams() {
        Map<String, Object> p = new HashMap<>();
        p.put("userId", "U-SMOKE");
        p.put("orderId", "ORD-SMOKE");
        p.put("amount", 300);
        p.put("payAmount", 100.0);
        p.put("productId", "PROD-SMOKE");
        p.put("quantity", 2);
        p.put("applyId", "APPLY-SMOKE");
        p.put("auditNo", "AUD-SMOKE");
        p.put("rawOrderId", "RAW-SMOKE");
        p.put("rawAmount", "200");
        p.put("keyword", "smoke");
        p.put("name", "smoke");
        p.put("step", 0);
        p.put("deviceId", "DEV-SMOKE");
        p.put("clientIp", "127.0.0.1");
        p.put("msgId", "MSG-SMOKE");
        return p;
    }
}
