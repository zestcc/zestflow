package com.zestflow.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainEdgeDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.ChainNodeDTO;
import com.zestflow.demo.service.BizOrchestrationService;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.http.ChainGateway;
import com.zestflow.executor.route.ChainRouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 三 Mode E2E：Mode 1 /execute、Mode 2 链路由、Mode 3 ChainGateway。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "zestflow.executor.execute-endpoint-enabled=true",
        "zestflow.executor.chain-route-enabled=true",
        "zestflow.executor.execute-response-mode=BODY",
        "zestflow.executor.execute-failure-policy=PROPAGATE"
})
class HttpThreeModeTest {

    private static final String CHAIN_CODE = "CHN_HTTP_THREE_MODE_TEST";
    private static final String MODE2_PATH = "/api/zestflow/demo/order-create-http";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BizOrchestrationService orch;

    @Autowired
    private ChainGateway chainGateway;

    @Autowired
    private ChainRouteRegistry chainRouteRegistry;

    @Autowired
    private ChainManager chainManager;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void loadHttpDemoChain() {
        Map<String, Object> chainConfig = new LinkedHashMap<>();
        chainConfig.put("http", Map.of(
                "path", MODE2_PATH,
                "method", "POST",
                "produces", "application/json"
        ));
        orch.loadAndExecute(CHAIN_CODE,
                List.of(
                        BizOrchestrationService.normalNode("validate", "validateUser"),
                        BizOrchestrationService.normalNode("create", "createOrder"),
                        BizOrchestrationService.normalNode("parse", "parseOrderCreateResponse")
                ),
                List.of(
                        BizOrchestrationService.edge("validate", "create"),
                        BizOrchestrationService.edge("create", "parse")
                ),
                Map.of("userId", "U-HTTP-001", "productId", "SKU-HTTP", "quantity", 1, "amount", 88.8),
                chainConfig);
        chainRouteRegistry.refresh(chainManager);
    }

    @Test
    void mode1ExecuteEndpointReturnsParserBody() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chainCode", CHAIN_CODE);
        body.put("params", Map.of("userId", "U-MODE1", "productId", "SKU-M1", "quantity", 1, "amount", 99.0));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/execute",
                new HttpEntity<>(json.writeValueAsString(body), headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = json.readTree(resp.getBody());
        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("mode").asText()).isEqualTo("PARSER");
        assertThat(node.has("orderId")).isTrue();
    }

    @Test
    void mode2ChainRouteReturnsParserBody() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> params = Map.of("userId", "U-MODE2", "productId", "SKU-M2", "quantity", 2, "amount", 128.0);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + MODE2_PATH,
                new HttpEntity<>(json.writeValueAsString(params), headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = json.readTree(resp.getBody());
        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("mode").asText()).isEqualTo("PARSER");
    }

    @Test
    void mode3ChainGatewayExecuteOrThrow() {
        String uniqueCode = CHAIN_CODE + "-GW-" + UUID.randomUUID().toString().substring(0, 6);
        orch.loadAndExecute(uniqueCode,
                List.of(
                        BizOrchestrationService.normalNode("validate", "validateUser"),
                        BizOrchestrationService.normalNode("create", "createOrder"),
                        BizOrchestrationService.normalNode("parse", "parseOrderCreateResponse")
                ),
                List.of(
                        BizOrchestrationService.edge("validate", "create"),
                        BizOrchestrationService.edge("create", "parse")
                ),
                Map.of("userId", "U-MODE3", "productId", "SKU-M3", "quantity", 1, "amount", 66.6));

        ChainExecuteResultDTO result = chainGateway.executeOrThrow(uniqueCode,
                Map.of("userId", "U-MODE3", "productId", "SKU-M3", "quantity", 1, "amount", 66.6));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getReturnValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) result.getReturnValue();
        assertThat(parsed.get("success")).isEqualTo(true);
        assertThat(parsed.get("mode")).isEqualTo("PARSER");
    }
}
