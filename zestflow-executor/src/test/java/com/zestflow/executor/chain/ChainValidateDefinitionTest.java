package com.zestflow.executor.chain;

import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChainValidateDefinitionTest {

    @Mock
    private ComponentScanner componentScanner;
    @Mock
    private ChainValidator chainValidator;
    @Mock
    private ChainDefinitionBuilder chainDefinitionBuilder;
    @Mock
    private ChainRepository chainRepo;
    @Mock
    private com.zestflow.executor.design.DesignRepository designRepo;
    @Mock
    private com.zestflow.executor.engine.NodeRunner nodeRunner;
    @Mock
    private com.zestflow.executor.registry.AdminClient adminClient;
    @Mock
    private com.zestflow.executor.registry.ExecutorProperties executorProperties;
    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.zestflow.executor.route.ChainRouteRegistry> chainRouteRegistryProvider;
    @Mock
    private ChainManager chainManager;

    private ChainLoader chainLoader;

    @BeforeEach
    void setUp() {
        chainLoader = new ChainLoader(chainManager, componentScanner, chainValidator, chainDefinitionBuilder,
                chainRepo, designRepo, nodeRunner, adminClient, executorProperties, chainRouteRegistryProvider);
    }

    @Test
    void validateDefinition_rejectsMissingChainCode() {
        ChainLoader.ChainValidationResult result =
                chainLoader.validateDefinition("", 1, "{}", null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).contains("chainCode 不能为空");
    }

    @Test
    void validateDefinition_rejectsEmptyPayload() {
        ChainLoader.ChainValidationResult result =
                chainLoader.validateDefinition("CHN001", 1, null, null);
        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validateDefinition_returnsValidatorErrors() {
        ChainDefinition def = org.mockito.Mockito.mock(ChainDefinition.class);
        when(chainDefinitionBuilder.build(any(), any(), any(), any())).thenReturn(def);
        when(chainValidator.validate(def)).thenReturn(List.of("节点[n1] 引用了不存在的组件: ghost"));

        ChainLoader.ChainValidationResult result =
                chainLoader.validateDefinition("CHN001", 1, "{\"nodes\":[]}", null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).contains("节点[n1] 引用了不存在的组件: ghost");
    }

    @Test
    void validateDefinition_successWhenNoErrors() {
        ChainDefinition def = org.mockito.Mockito.mock(ChainDefinition.class);
        when(chainDefinitionBuilder.build(any(), any(), any(), any())).thenReturn(def);
        when(chainValidator.validate(def)).thenReturn(List.of());

        ChainLoader.ChainValidationResult result =
                chainLoader.validateDefinition("CHN001", 1, "{\"nodes\":[],\"edges\":[]}", null);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }
}
