package com.zestflow.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainEdgeDTO;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.ChainNodeDTO;
import com.zestflow.common.model.event.PublishEventDTO;
import com.zestflow.executor.server.ExecutorServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内联脚本判断节点 — Admin 代理目标（Executor Netty）HTTP 全链路测试。
 * <p>
 * 流程：创建设计 → 创建链 → 绑定 → 保存图谱 → 发布热加载 → /execute 触发。
 * 对标 Admin DesignController.saveGraph + ChainController.publish + ExecutorProxyService。
 */
@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DemoApplication.class)
@ActiveProfiles("test")
class InlineScriptPredicateHttpFlowTest {

    private static final String UPDATED_BY = "http-flow-test";
    private static final Map<String, Object> PREDICATE_CONFIG = Map.of(
            "predicateMode", "script",
            "predicateScript", "StringUtils.hasText(supplierType)",
            "trueLabel", "True",
            "falseLabel", "False"
    );

    @Autowired
    private ExecutorServer executorServer;

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate rest = new RestTemplate();

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + executorServer.getPort();
    }

    @Test
    void httpFlow_inlineScriptPredicateRoutesTrueBranch() throws Exception {
        String chainCode = setupAndPublishChain();
        ChainExecuteResultDTO result = executeChain(chainCode, Map.of(
                "supplierType", "OTA",
                "userId", "U001"
        ));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).extracting(nr -> nr.getNodeId())
                .containsExactly("start", "cond", "pass", "end");
    }

    @Test
    void httpFlow_inlineScriptPredicateRoutesFalseBranch() throws Exception {
        String chainCode = setupAndPublishChain();
        ChainExecuteResultDTO result = executeChain(chainCode, Map.of("userId", "U001"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).extracting(nr -> nr.getNodeId())
                .containsExactly("start", "cond", "fail", "end");
    }

    /** 设计保存 → 发布 → 返回链编码 */
    private String setupAndPublishChain() throws Exception {
        String suffix = String.valueOf(System.nanoTime());

        JsonNode design = post("/api/designs", Map.of(
                "name", "内联脚本判断设计-" + suffix,
                "description", "HTTP 全链路测试",
                "updatedBy", UPDATED_BY
        ));
        String designCode = design.get("code").asText();
        assertThat(designCode).startsWith("DSN");

        JsonNode chain = post("/api/chains", Map.of(
                "name", "内联脚本判断链-" + suffix,
                "updatedBy", UPDATED_BY
        ));
        String chainCode = chain.get("code").asText();
        assertThat(chainCode).startsWith("CHN");

        JsonNode bindResp = post("/api/designs/" + designCode + "/bindings", Map.of(
                "chainCode", chainCode,
                "updatedBy", UPDATED_BY
        ));
        assertThat(bindResp.get("code").asInt()).isEqualTo(200);

        String chainDataJson = buildPredicateChainDataJson();
        JsonNode saveResp = put("/api/designs/" + designCode + "/graph", Map.of(
                "graphData", "{}",
                "chainData", chainDataJson,
                "updatedBy", UPDATED_BY
        ));
        assertThat(saveResp.get("code").asInt()).isEqualTo(200);
        assertThat(saveResp.get("flowValid").asBoolean()).isTrue();

        PublishEventDTO reloadEvent = putForObject(
                "/api/chains/" + chainCode + "/reload",
                Map.of("updatedBy", UPDATED_BY),
                PublishEventDTO.class
        );
        assertThat(reloadEvent.getSuccess()).isTrue();
        assertThat(reloadEvent.getNodeCount()).isGreaterThan(0);
        log.info("链发布成功 chainCode={} designCode={} nodes={}", chainCode, designCode, reloadEvent.getNodeCount());
        return chainCode;
    }

    private ChainExecuteResultDTO executeChain(String chainCode, Map<String, Object> params) {
        ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()
                .chainCode(chainCode)
                .params(params)
                .source("http-flow-test")
                .build();
        return postForObject("/execute", request, ChainExecuteResultDTO.class);
    }

    private String buildPredicateChainDataJson() throws Exception {
        ChainNodeDTO cond = ChainNodeDTO.builder()
                .id("cond")
                .label("hasSupplier")
                .type("CONDITION")
                .component("INLINE_PRED_HTTP")
                .componentName("hasSupplier")
                .config(PREDICATE_CONFIG)
                .build();
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .version(1)
                .nodes(List.of(
                        normalNode("start", "validateUser"),
                        cond,
                        normalNode("pass", "processPayment"),
                        normalNode("fail", "deductStock"),
                        normalNode("end", "sendNotify")
                ))
                .edges(List.of(
                        edge("start", "cond"),
                        edge("cond", "pass", "True"),
                        edge("cond", "fail", "False"),
                        edge("pass", "end"),
                        edge("fail", "end")
                ))
                .config(Map.of("errorStrategy", ChainConstants.ERROR_STRATEGY_STOP))
                .build();
        return mapper.writeValueAsString(dto);
    }

    private static ChainNodeDTO normalNode(String id, String component) {
        return ChainNodeDTO.builder().id(id).label(id).type("NORMAL").component(component).build();
    }

    private static ChainEdgeDTO edge(String source, String target) {
        return ChainEdgeDTO.builder().source(source).target(target).build();
    }

    private static ChainEdgeDTO edge(String source, String target, String label) {
        return ChainEdgeDTO.builder().source(source).target(target).label(label).build();
    }

    private JsonNode post(String path, Object body) throws Exception {
        ResponseEntity<String> resp = exchange(path, HttpMethod.POST, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(resp.getBody());
    }

    private JsonNode put(String path, Object body) throws Exception {
        ResponseEntity<String> resp = exchange(path, HttpMethod.PUT, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(resp.getBody());
    }

    private <T> T postForObject(String path, Object body, Class<T> type) {
        ResponseEntity<String> resp = exchange(path, HttpMethod.POST, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return mapper.readValue(resp.getBody(), type);
        } catch (Exception e) {
            throw new IllegalStateException("响应解析失败 path=" + path + " body=" + resp.getBody(), e);
        }
    }

    private <T> T putForObject(String path, Object body, Class<T> type) {
        ResponseEntity<String> resp = exchange(path, HttpMethod.PUT, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return mapper.readValue(resp.getBody(), type);
        } catch (Exception e) {
            throw new IllegalStateException("响应解析失败 path=" + path + " body=" + resp.getBody(), e);
        }
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return rest.exchange(baseUrl + path, method, entity, String.class);
    }
}
