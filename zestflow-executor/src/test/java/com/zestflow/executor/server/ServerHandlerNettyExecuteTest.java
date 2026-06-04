package com.zestflow.executor.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.executor.chain.ChainRepository;
import com.zestflow.executor.design.DesignRepository;
import com.zestflow.executor.engine.ChainExecutionEngine;
import com.zestflow.executor.http.ChainExecuteFacade;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Netty {@code POST /execute} 契约测试 — 固定 DETAIL，供 Admin 试验场解析 instanceId/nodeResults。
 */
@ExtendWith(MockitoExtension.class)
class ServerHandlerNettyExecuteTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private ChainExecutionEngine chainExecutionEngine;
    @Mock
    private ChainRepository chainRepository;
    @Mock
    private DesignRepository designRepository;
    @Mock
    private ChainExecuteFacade chainExecuteFacade;

    private ServerHandler serverHandler;

    @BeforeEach
    void setUp() {
        serverHandler = new ServerHandler(chainExecutionEngine, chainRepository, designRepository);
        serverHandler.setChainExecuteFacade(chainExecuteFacade);
    }

    @Test
    void nettyExecuteReturnsFullDetailDtoOnSuccess() throws Exception {
        ChainExecuteResultDTO detail = ChainExecuteResultDTO.builder()
                .instanceId("inst-netty-001")
                .chainCode("CHN_DEMO_NODE_1")
                .status(ChainConstants.CHAIN_SUCCESS)
                .costMs(15L)
                .nodeResults(List.of(NodeResultDTO.builder()
                        .nodeId("n1")
                        .status(ChainConstants.NODE_SUCCESS)
                        .costMs(10L)
                        .build()))
                .build();
        when(chainExecuteFacade.executeCore(any(ChainExecuteRequestDTO.class))).thenReturn(detail);

        FullHttpResponse response = postExecute("{\"chainCode\":\"CHN_DEMO_NODE_1\",\"params\":{\"userId\":\"U1\"}}");

        assertThat(response.status()).isEqualTo(HttpResponseStatus.OK);
        JsonNode body = JSON.readTree(response.content().toString(CharsetUtil.UTF_8));
        assertThat(body.get("instanceId").asText()).isEqualTo("inst-netty-001");
        assertThat(body.get("chainCode").asText()).isEqualTo("CHN_DEMO_NODE_1");
        assertThat(body.get("status").asInt()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(body.get("nodeResults").isArray()).isTrue();
        assertThat(body.get("nodeResults")).hasSize(1);

        verify(chainExecuteFacade).executeCore(any(ChainExecuteRequestDTO.class));
        verify(chainExecuteFacade, never()).executeHttp(any());
    }

    @Test
    void nettyExecuteReturnsFullDetailDtoOnFailureWithHttp200() throws Exception {
        ChainExecuteResultDTO detail = ChainExecuteResultDTO.builder()
                .instanceId("inst-netty-fail")
                .chainCode("CHN_FAIL")
                .status(ChainConstants.CHAIN_FAILED)
                .errorMessage("node boom")
                .nodeResults(List.of(NodeResultDTO.builder()
                        .nodeId("bad")
                        .status(ChainConstants.NODE_FAILED)
                        .errorMessage("node boom")
                        .build()))
                .build();
        when(chainExecuteFacade.executeCore(any(ChainExecuteRequestDTO.class))).thenReturn(detail);

        FullHttpResponse response = postExecute("{\"chainCode\":\"CHN_FAIL\"}");

        assertThat(response.status()).isEqualTo(HttpResponseStatus.OK);
        JsonNode body = JSON.readTree(response.content().toString(CharsetUtil.UTF_8));
        assertThat(body.get("instanceId").asText()).isEqualTo("inst-netty-fail");
        assertThat(body.get("status").asInt()).isEqualTo(ChainConstants.CHAIN_FAILED);
        assertThat(body.get("errorMessage").asText()).isEqualTo("node boom");
        assertThat(body.get("nodeResults").isArray()).isTrue();
    }

    private FullHttpResponse postExecute(String jsonBody) {
        EmbeddedChannel channel = new EmbeddedChannel(serverHandler);
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/execute",
                Unpooled.copiedBuffer(jsonBody, CharsetUtil.UTF_8));
        request.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                .set(HttpHeaderNames.CONTENT_LENGTH, jsonBody.length());
        channel.writeInbound(request);
        return channel.readOutbound();
    }
}
