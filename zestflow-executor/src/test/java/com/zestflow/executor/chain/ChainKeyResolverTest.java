package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainExecutionErrorCodes;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.executor.registry.ExecutorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChainKeyResolverTest {

    @Mock
    private ChainRepository chainRepository;

    private ChainKeyResolver resolver;
    private ExecutorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ExecutorProperties();
        properties.setAppCode("demo-app");
        resolver = new ChainKeyResolver(chainRepository, properties);
    }

    @Test
    void resolveKeyNotRegistered() {
        when(chainRepository.getByChainKey("demo-app", "missing.key")).thenReturn(null);

        ChainKeyResolver.ResolvedChainKey resolved = resolver.resolveKey("missing.key");

        assertThat(resolved.isOk()).isFalse();
        assertThat(resolved.failure().getErrorCode()).isEqualTo(ChainExecutionErrorCodes.CHAIN_KEY_NOT_REGISTERED);
    }

    @Test
    void resolveKeyNotDesigned() {
        ChainPO po = ChainPO.builder()
                .code("CHN001")
                .chainKey("demo.chain")
                .status(ChainLifecycleStatus.DESIGNING)
                .build();
        when(chainRepository.getByChainKey("demo-app", "demo.chain")).thenReturn(po);

        ChainKeyResolver.ResolvedChainKey resolved = resolver.resolveKey("demo.chain");

        assertThat(resolved.isOk()).isFalse();
        assertThat(resolved.failure().getErrorCode()).isEqualTo(ChainExecutionErrorCodes.CHAIN_NOT_DESIGNED);
    }

    @Test
    void resolveKeyPublished() {
        ChainPO po = ChainPO.builder()
                .code("CHN001")
                .chainKey("demo.chain")
                .status(ChainLifecycleStatus.PUBLISHED)
                .build();
        when(chainRepository.getByChainKey("demo-app", "demo.chain")).thenReturn(po);

        ChainKeyResolver.ResolvedChainKey resolved = resolver.resolveKey("demo.chain");

        assertThat(resolved.isOk()).isTrue();
        assertThat(resolved.chainCode()).isEqualTo("CHN001");
    }

    @Test
    void readinessFailureChainNotFound() {
        when(chainRepository.get("CHN404")).thenReturn(null);

        ChainExecuteResultDTO failure = resolver.readinessFailure("CHN404");

        assertThat(failure.getErrorCode()).isEqualTo(ChainExecutionErrorCodes.CHAIN_NOT_FOUND);
    }
}
