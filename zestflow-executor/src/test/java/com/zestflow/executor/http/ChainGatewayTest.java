package com.zestflow.executor.http;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.exception.ChainExecutionException;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChainGatewayTest {

    @Mock
    private ChainExecuteFacade facade;

    private ChainGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new ChainGateway(facade);
    }

    @Test
    void executeOrThrowSuccess() {
        ChainExecuteResultDTO result = ChainExecuteResultDTO.builder()
                .status(ChainConstants.CHAIN_SUCCESS)
                .finalReturnValue("done")
                .build();
        when(facade.executeCore(any(), any(Object[].class))).thenReturn(result);

        ChainExecuteResultDTO out = gateway.executeOrThrow("CHN001", Map.of("k", "v"));
        assertThat(out.getReturnValue()).isEqualTo("done");
    }

    @Test
    void executeOrThrowFailureThrows() {
        ChainExecuteResultDTO result = ChainExecuteResultDTO.builder()
                .status(ChainConstants.CHAIN_FAILED)
                .errorMessage("boom")
                .build();
        when(facade.executeCore(any(), any(Object[].class))).thenReturn(result);

        assertThatThrownBy(() -> gateway.executeOrThrow("CHN001"))
                .isInstanceOf(ChainExecutionException.class);
    }
}
