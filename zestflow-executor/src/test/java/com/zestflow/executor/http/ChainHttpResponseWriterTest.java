package com.zestflow.executor.http;

import com.zestflow.common.exception.ChainExecutionException;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.protocol.ChainFailurePolicy;
import com.zestflow.common.protocol.ChainHttpResponseMode;
import com.zestflow.common.protocol.ChainHttpRouteConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChainHttpResponseWriterTest {

    @Test
    void successBodyModeReturnsParserOutput() {
        ChainExecuteResultDTO result = ChainExecuteResultDTO.builder()
                .status(com.zestflow.common.constant.ChainConstants.CHAIN_SUCCESS)
                .finalReturnValue("<HotelList/>")
                .build();
        ResponseEntity<?> resp = ChainHttpResponseWriter.success(result, null, ChainHttpResponseMode.BODY);
        assertThat(resp.getBody()).isEqualTo("<HotelList/>");
    }

    @Test
    void propagateFailureThrowsChainExecutionException() {
        ChainExecuteResultDTO result = ChainExecuteResultDTO.builder()
                .status(com.zestflow.common.constant.ChainConstants.CHAIN_FAILED)
                .errorMessage("boom")
                .build();
        assertThatThrownBy(() -> ChainHttpResponseWriter.handleFailure(
                result, null, ChainFailurePolicy.PROPAGATE, null, null))
                .isInstanceOf(ChainExecutionException.class);
    }

    @Test
    void wrappedFailureReturnsSuccessFalseMap() {
        ChainExecuteResultDTO result = ChainExecuteResultDTO.builder()
                .chainCode("CHN001")
                .status(com.zestflow.common.constant.ChainConstants.CHAIN_FAILED)
                .errorMessage("err")
                .build();
        ResponseEntity<?> resp = ChainHttpResponseWriter.handleFailure(
                result, null, ChainFailurePolicy.WRAPPED, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(body.get("errorMessage")).isEqualTo("err");
    }

    @Test
    void errorHandlerFallbackWhenHandlerMissing() {
        ChainErrorHandlerInvoker invoker = mock(ChainErrorHandlerInvoker.class);
        when(invoker.invoke(any(), any(), any(), any())).thenReturn(null);

        ChainExecuteResultDTO result = ChainExecuteResultDTO.builder()
                .chainCode("CHN001")
                .status(com.zestflow.common.constant.ChainConstants.CHAIN_FAILED)
                .errorMessage("node failed")
                .build();
        ResponseEntity<?> resp = ChainHttpResponseWriter.handleFailure(
                result,
                ChainHttpRouteConfig.builder()
                        .errorHandler("missingHandler")
                        .failurePolicy(ChainFailurePolicy.ERROR_HANDLER)
                        .build(),
                ChainFailurePolicy.PROPAGATE,
                invoker,
                null);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(body.get("errorMessage")).isEqualTo("node failed");
    }
}
