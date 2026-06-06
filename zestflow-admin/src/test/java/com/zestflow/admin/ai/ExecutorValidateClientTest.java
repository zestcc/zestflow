package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.vo.AiValidationVO;
import com.zestflow.admin.client.ExecutorProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorValidateClientTest {

    @Mock
    private ExecutorProxyService executorProxyService;

    private ExecutorValidateClient client;

    @BeforeEach
    void setUp() {
        client = new ExecutorValidateClient(executorProxyService);
    }

    @Test
    void validate_wrapsChainCodeInRequestBody() {
        when(executorProxyService.resolveExecutorBaseUrl("demo-app")).thenReturn("http://127.0.0.1:20550");
        when(executorProxyService.executeOnExecutorUrl(anyString(), eq("POST"), eq("/api/chains/validate-definition"), anyString()))
                .thenAnswer(inv -> {
                    String body = inv.getArgument(3);
                    assertThat(body).contains("\"chainCode\":\"CHN_TEST\"");
                    assertThat(body).contains("\"chainData\"");
                    return new ExecutorProxyService.ExecutorResult("http://127.0.0.1:20550", true, null,
                            "{\"valid\":true,\"errors\":[]}");
                });

        AiValidationVO vo = client.validate("demo-app",
                "{\"code\":\"CHN_TEST\",\"version\":1,\"nodes\":[],\"edges\":[]}");
        assertThat(vo.isValid()).isTrue();
    }

    @Test
    void parseValidationResult_handlesPlainExecutorJson() {
        ExecutorProxyService.ExecutorResult result = new ExecutorProxyService.ExecutorResult(
                "http://x", true, null, "{\"valid\":false,\"errors\":[\"bad component\"]}");
        AiValidationVO vo = client.parseValidationResult(result);
        assertThat(vo.isValid()).isFalse();
        assertThat(vo.getErrors()).contains("bad component");
    }
}
