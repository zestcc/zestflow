package com.zestflow.demo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainEdgeDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.ChainNodeDTO;
import com.zestflow.executor.chain.ChainDefinitionBuilder;
import com.zestflow.executor.chain.ChainRuntimeRegistrar;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.engine.ChainExecutionEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 151 条演示链 0→1 矩阵测试：从 demo-chains.json 加载拓扑，在引擎内全量执行。
 */
@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoChainMatrixTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> HEAVY_CHAINS = Set.of("CHN_DEMO_STRESS_75");

    @LocalServerPort
    private int serverPort;

    @Autowired
    private ChainDefinitionBuilder chainDefinitionBuilder;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private ChainExecutionEngine chainExecutionEngine;
    @Autowired
    private ChainRuntimeRegistrar chainRuntimeRegistrar;

    private Map<String, Map<String, Object>> sceneParamsByChain = Map.of();

    @BeforeAll
    void loadAllDemoChains() throws Exception {
        Path jsonPath = Path.of("scripts/seed/demo-chains.json");
        if (!jsonPath.toFile().exists()) {
            jsonPath = Path.of("../scripts/seed/demo-chains.json");
        }
        JsonNode root = MAPPER.readTree(jsonPath.toFile());
        JsonNode chainsNode = root.get("chains");
        JsonNode scenesNode = root.get("scenes");

        Map<String, Map<String, Object>> paramsByChain = new HashMap<>();
        if (scenesNode != null && scenesNode.isArray()) {
            for (JsonNode scene : scenesNode) {
                String chainCode = scene.path("chain").asText();
                Map<String, Object> body = parseBody(scene.path("body").asText("{}"));
                paramsByChain.merge(chainCode, body, (a, b) -> {
                    Map<String, Object> merged = new HashMap<>(a);
                    merged.putAll(b);
                    return merged;
                });
            }
        }
        sceneParamsByChain = paramsByChain;

        List<JsonNode> chainList = new ArrayList<>();
        chainsNode.forEach(chainList::add);
        for (JsonNode chainJson : chainList) {
            ChainDefinitionDTO dto = toChainDto(chainJson, root);
            chainManager.load(chainDefinitionBuilder.build(dto));
            chainRuntimeRegistrar.ensurePublished(dto.getCode());
        }
        log.info("DemoChainMatrixTest 已注册链条数={}", chainList.size());
    }

    Stream<ChainCase> chainCases() throws Exception {
        Path jsonPath = Path.of("scripts/seed/demo-chains.json");
        if (!jsonPath.toFile().exists()) {
            jsonPath = Path.of("../scripts/seed/demo-chains.json");
        }
        JsonNode root = MAPPER.readTree(jsonPath.toFile());
        Map<String, Map<String, Object>> paramsByChain = new HashMap<>();
        JsonNode scenesNode = root.get("scenes");
        if (scenesNode != null && scenesNode.isArray()) {
            for (JsonNode scene : scenesNode) {
                String chainCode = scene.path("chain").asText();
                paramsByChain.merge(chainCode, parseBody(scene.path("body").asText("{}")), (a, b) -> {
                    Map<String, Object> merged = new HashMap<>(a);
                    merged.putAll(b);
                    return merged;
                });
            }
        }

        String httpEchoUrl = "http://127.0.0.1:" + serverPort + "/test/http-echo";
        List<ChainCase> cases = new ArrayList<>();
        for (JsonNode chainJson : root.get("chains")) {
            String code = chainJson.path("code").asText();
            String tier = chainJson.path("tier").asText("1-3");
            Map<String, Object> params = defaultParams(httpEchoUrl);
            params.putAll(paramsByChain.getOrDefault(code, Map.of()));
            params.put("_http_url", httpEchoUrl);
            boolean heavy = HEAVY_CHAINS.contains(code) || "70-80".equals(tier);
            cases.add(new ChainCase(code, tier, params, heavy));
        }
        log.info("DemoChainMatrixTest 用例数={}", cases.size());
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chainCases")
    void executeDemoChainFromZero(ChainCase chainCase) {
        if (chainCase.heavy()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    Boolean.getBoolean("zestflow.matrix.includeHeavy"),
                    "跳过压力链，设置 -Dzestflow.matrix.includeHeavy=true 启用");
        }

        ChainExecuteResultDTO result = chainExecutionEngine.execute(chainCase.code(), chainCase.params());
        assertThat(result.getStatus())
                .as("链 %s 应执行成功 status=%s error=%s", chainCase.code(), result.getStatus(), result.getErrorMessage())
                .isEqualTo(ChainConstants.CHAIN_SUCCESS);
    }

    private static ChainDefinitionDTO toChainDto(JsonNode chainJson, JsonNode root) throws Exception {
        String code = chainJson.path("code").asText();
        Graph graph = resolveGraph(chainJson, root);
        List<ChainNodeDTO> nodes = MAPPER.convertValue(graph.nodes(), new TypeReference<>() {});
        List<ChainEdgeDTO> edges = MAPPER.convertValue(graph.edges(), new TypeReference<>() {});
        Map<String, Object> config = new HashMap<>();
        if (chainJson.hasNonNull("errorStrategy")) {
            config.put("errorStrategy", chainJson.get("errorStrategy").asText());
        }
        return ChainDefinitionDTO.builder()
                .code(code)
                .version(1)
                .nodes(nodes)
                .edges(edges)
                .config(config)
                .build();
    }

    private static Graph resolveGraph(JsonNode chainJson, JsonNode root) {
        if (chainJson.hasNonNull("serialFrom")) {
            String listKey = chainJson.get("serialFrom").asText();
            JsonNode compList = root.get(listKey);
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();
            int i = 1;
            for (JsonNode comp : compList) {
                String nid = "n" + i;
                nodes.add(Map.of(
                        "id", nid,
                        "label", "步骤" + i,
                        "type", "NORMAL",
                        "component", comp.asText()
                ));
                if (i > 1) {
                    edges.add(Map.of("source", "n" + (i - 1), "target", nid));
                }
                i++;
                if (i > 75) {
                    break;
                }
            }
            return new Graph(nodes, edges);
        }
        List<Map<String, Object>> nodes = MAPPER.convertValue(chainJson.get("nodes"), new TypeReference<>() {});
        List<Map<String, Object>> edges = chainJson.has("edges")
                ? MAPPER.convertValue(chainJson.get("edges"), new TypeReference<>() {})
                : List.of();
        return new Graph(nodes, edges);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBody(String bodyJson) {
        try {
            return MAPPER.readValue(bodyJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Map<String, Object> defaultParams(String httpEchoUrl) {
        Map<String, Object> p = new HashMap<>();
        p.put("userId", "U-MATRIX");
        p.put("orderId", "ORD-MATRIX-001");
        p.put("amount", 128.5);
        p.put("payAmount", 128.5);
        p.put("productId", "PROD-MATRIX");
        p.put("quantity", 2);
        p.put("applyId", "APPLY-MATRIX");
        p.put("step", 0);
        p.put("channel", "APP");
        p.put("payMethod", "WECHAT");
        p.put("orderStatus", "PENDING");
        p.put("auditResult", "APPROVED");
        p.put("cacheKey", "matrix-key");
        p.put("cacheValue", "matrix-value");
        p.put("keyword", "matrix");
        p.put("name", "matrix-user");
        p.put("orderList", List.of(
                Map.of("orderId", "ORD-001", "amount", 100, "orderStatus", "PAID"),
                Map.of("orderId", "ORD-002", "amount", 200, "orderStatus", "PENDING")
        ));
        p.put("userList", List.of(
                Map.of("userId", "U1", "status", "ACTIVE"),
                Map.of("userId", "U2", "status", "INACTIVE")
        ));
        p.put("itemList", List.of(Map.of("sku", "SKU1", "qty", 1)));
        p.put("notifyItems", List.of(
                Map.of("userId", "U1", "message", "hello"),
                Map.of("userId", "U2", "message", "world")
        ));
        p.put("_http_url", httpEchoUrl);
        return p;
    }

    record ChainCase(String code, String tier, Map<String, Object> params, boolean heavy) {
        @Override
        public String toString() {
            return code;
        }
    }

    private record Graph(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {}
}
