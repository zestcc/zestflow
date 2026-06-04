package com.zestflow.executor.http;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.exception.ChainExecutionException;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.protocol.ChainHttpResponseMode;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.engine.ChainExecutionEngine;
import com.zestflow.executor.engine.ChainInstance;
import com.zestflow.executor.engine.ExecutionIdempotencyGuard;
import com.zestflow.executor.registry.ExecutorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ChainExecuteFacadeTest {

    @Mock
    private ChainManager chainManager;
    @Mock
    private ChainErrorHandlerInvoker errorHandlerInvoker;

    private ChainExecuteFacade facade;
    private StubEngine stubEngine;

    @BeforeEach
    void setUp() {
        stubEngine = new StubEngine();
        ExecutorProperties properties = new ExecutorProperties();
        properties.setExecuteResponseMode(ChainHttpResponseMode.BODY);
        properties.setIdempotencyEnabled(false);
        facade = new ChainExecuteFacade(stubEngine, chainManager, new ExecutionIdempotencyGuard(),
                properties, errorHandlerInvoker);
    }

    @Test
    void executeHttpSuccessReturnsParserBody() {
        stubEngine.nextResult = ChainExecuteResultDTO.builder()
                .chainCode("CHN001")
                .status(ChainConstants.CHAIN_SUCCESS)
                .finalReturnValue(Map.of("ok", true))
                .build();

        ResponseEntity<?> resp = facade.executeHttp(ChainExecuteRequestDTO.builder()
                .chainCode("CHN001")
                .params(Map.of("userId", "U1"))
                .build());

        assertThat(resp.getBody()).isEqualTo(Map.of("ok", true));
    }

    @Test
    void executeHttpFailurePropagateThrows() {
        stubEngine.nextResult = ChainExecuteResultDTO.builder()
                .chainCode("CHN001")
                .status(ChainConstants.CHAIN_FAILED)
                .errorMessage("fail")
                .build();

        assertThatThrownBy(() -> facade.executeHttp(ChainExecuteRequestDTO.builder()
                .chainCode("CHN001")
                .build()))
                .isInstanceOf(ChainExecutionException.class);
    }

    private static final class StubEngine implements ChainExecutionEngine {
        private ChainExecuteResultDTO nextResult;

        @Override
        public ChainExecuteResultDTO execute(String chainCode, Object... args) {
            return nextResult;
        }

        @Override
        public ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params, Object... args) {
            return nextResult;
        }

        @Override
        public ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params,
                                             Map<String, String> headers, Object... args) {
            return nextResult;
        }

        @Override
        public ChainExecuteResultDTO executeWithDeadline(String chainCode, Map<String, Object> params, long parentDeadlineMs) {
            return nextResult;
        }

        @Override
        public CompletableFuture<ChainExecuteResultDTO> executeAsync(String chainCode, Object... args) {
            return CompletableFuture.completedFuture(nextResult);
        }

        @Override
        public boolean stop(String instanceId) {
            return false;
        }

        @Override
        public int stopByChain(String chainCode) {
            return 0;
        }

        @Override
        public List<ChainInstance> listRunning(String chainCode) {
            return List.of();
        }
    }
}
