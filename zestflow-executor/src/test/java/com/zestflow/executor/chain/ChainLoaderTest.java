package com.zestflow.executor.chain;

import com.zestflow.executor.design.DesignPO;
import com.zestflow.executor.design.DesignRepository;
import com.zestflow.executor.engine.NodeRunner;
import com.zestflow.executor.registry.AdminClient;
import com.zestflow.executor.registry.ExecutorProperties;
import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChainLoaderTest {

    @Mock ChainManager chainManager;
    @Mock ComponentScanner componentScanner;
    @Mock ChainValidator chainValidator;
    @Mock ChainDefinitionBuilder chainDefinitionBuilder;
    @Mock ChainRepository chainRepo;
    @Mock DesignRepository designRepo;
    @Mock NodeRunner nodeRunner;
    @Mock AdminClient adminClient;
    @Mock ExecutorProperties executorProperties;

    @Captor ArgumentCaptor<Set<String>> nodeIdCaptor;

    private ChainLoader chainLoader;

    @BeforeEach
    void setUp() {
        when(executorProperties.getAppCode()).thenReturn("test-app");
        when(executorProperties.getHost()).thenReturn("127.0.0.1");
        when(executorProperties.getPort()).thenReturn(20550);
        chainLoader = new ChainLoader(chainManager, componentScanner,
                chainValidator, chainDefinitionBuilder, chainRepo, designRepo, nodeRunner, adminClient, executorProperties);
    }

    // ==================== reloadChainLocal — version snapshot ====================

    @Test
    void reloadChainLocalSavesVersionSnapshotAfterSuccessfulReload() throws Exception {
        ChainPO chain = ChainPO.builder()
                .code("CHN001").status(4).designCode("DSN001").build();
        DesignPO design = DesignPO.builder()
                .code("DSN001").graphData("{\"nodes\":[]}").chainData("{\"version\":1}").build();
        ChainDefinition definition = mock(ChainDefinition.class);
        when(definition.getCode()).thenReturn("CHN001");
        when(definition.nodeCount()).thenReturn(3);

        when(chainRepo.get("CHN001")).thenReturn(chain);
        when(designRepo.get("DSN001")).thenReturn(design);
        when(chainDefinitionBuilder.build(eq("CHN001"), anyInt(), anyString(), anyString()))
                .thenReturn(definition);
        when(chainValidator.validate(definition)).thenReturn(List.of());
        when(chainRepo.incrementVersion("CHN001")).thenReturn(2);

        ChainLoader.ChainReloadResult result = chainLoader.reloadChainLocal("CHN001", null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNodeCount()).isEqualTo(3);

        verify(chainRepo).incrementVersion("CHN001");
        verify(chainRepo).saveVersionSnapshot("CHN001", 2, "DSN001",
                "{\"nodes\":[]}", "{\"version\":1}", null);
    }

    @Test
    void reloadChainLocalDoesNotSaveVersionOnValidationFailure() throws Exception {
        ChainPO chain = ChainPO.builder()
                .code("CHN001").status(4).designCode("DSN001").version(1).build();
        DesignPO design = DesignPO.builder()
                .code("DSN001").graphData("{\"nodes\":[]}").chainData("{\"version\":1}").build();

        when(chainRepo.get("CHN001")).thenReturn(chain);
        when(designRepo.get("DSN001")).thenReturn(design);
        when(chainDefinitionBuilder.build(anyString(), any(), anyString(), anyString()))
                .thenReturn(mock(ChainDefinition.class));
        when(chainValidator.validate(any())).thenReturn(List.of("节点缺失"));

        ChainLoader.ChainReloadResult result = chainLoader.reloadChainLocal("CHN001", null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("校验失败");

        verify(chainRepo, never()).incrementVersion(anyString());
        verify(chainRepo, never()).saveVersionSnapshot(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    // ==================== reloadChainLocal — ChainSync notification ====================

    @Test
    void reloadChainLocalNotifiesAdminOnSuccess() throws Exception {
        ChainPO chain = ChainPO.builder()
                .code("CHN001").status(4).designCode("DSN001").build();
        DesignPO design = DesignPO.builder()
                .code("DSN001").graphData("{}").chainData("{}").build();
        ChainDefinition definition = mock(ChainDefinition.class);
        when(definition.getCode()).thenReturn("CHN001");
        when(definition.nodeCount()).thenReturn(2);

        when(chainRepo.get("CHN001")).thenReturn(chain);
        when(designRepo.get("DSN001")).thenReturn(design);
        when(chainDefinitionBuilder.build(anyString(), any(), anyString(), anyString()))
                .thenReturn(definition);
        when(chainValidator.validate(definition)).thenReturn(List.of());
        when(chainRepo.incrementVersion("CHN001")).thenReturn(1);

        chainLoader.reloadChainLocal("CHN001", null, null);

        verify(adminClient).notifyChainSync(argThat(sync ->
                "CHN001".equals(sync.getLoadedChains().get(0))
                        && "READY".equals(sync.getStatus())
        ));
    }

    @Test
    void reloadChainLocalDoesNotNotifyOnFailure() throws Exception {
        when(chainRepo.get("CHN001")).thenReturn(null);

        chainLoader.reloadChainLocal("CHN001", null, null);

        verify(adminClient, never()).notifyChainSync(any());
    }

    @Test
    void reloadChainLocalAdminClientNullDoesNotThrow() {
        ChainLoader loaderWithoutAdmin = new ChainLoader(chainManager, componentScanner,
                chainValidator, chainDefinitionBuilder, chainRepo, designRepo, nodeRunner, null, executorProperties);

        ChainPO chain = ChainPO.builder()
                .code("CHN001").status(0).build();
        when(chainRepo.get("CHN001")).thenReturn(chain);

        ChainLoader.ChainReloadResult result = loaderWithoutAdmin.reloadChainLocal("CHN001", null, null);

        assertThat(result.isSuccess()).isFalse();
        // No exception thrown
    }

    // ==================== reloadChainLocal — circuit breaker cleanup ====================

    @Test
    void reloadChainLocalClearsOldCircuitBreakers() throws Exception {
        ChainPO chain = ChainPO.builder()
                .code("CHN001").status(4).designCode("DSN001").build();
        DesignPO design = DesignPO.builder()
                .code("DSN001").graphData("{}").chainData("{}").build();

        NodeDefinition oldNode1 = NodeDefinition.builder().id("old-n1").build();
        NodeDefinition oldNode2 = NodeDefinition.builder().id("old-n2").build();

        ChainDefinition oldDef = mock(ChainDefinition.class);
        when(oldDef.getNodes()).thenReturn(Map.of("old-n1", oldNode1, "old-n2", oldNode2));

        ChainDefinition newDef = mock(ChainDefinition.class);
        when(newDef.getCode()).thenReturn("CHN001");
        when(newDef.nodeCount()).thenReturn(2);

        when(chainRepo.get("CHN001")).thenReturn(chain);
        when(designRepo.get("DSN001")).thenReturn(design);
        when(chainManager.get("CHN001")).thenReturn(oldDef);
        when(chainDefinitionBuilder.build(anyString(), any(), anyString(), anyString()))
                .thenReturn(newDef);
        when(chainValidator.validate(newDef)).thenReturn(List.of());
        when(chainRepo.incrementVersion("CHN001")).thenReturn(1);

        chainLoader.reloadChainLocal("CHN001", null, null);

        verify(nodeRunner).clearCircuitBreakers(nodeIdCaptor.capture());
        assertThat(nodeIdCaptor.getValue()).contains("old-n1", "old-n2");
    }

    // ==================== loadAllChains — ChainSync notification ====================

    @Test
    void loadAllChainsNotifiesAdminOnSuccess() {
        ChainPO chain = ChainPO.builder()
                .code("CHN001").status(4).designCode("DSN001").version(1).build();
        DesignPO design = DesignPO.builder()
                .code("DSN001").graphData("{\"nodes\":[]}").chainData("{\"version\":1}").build();
        ChainDefinition definition = mock(ChainDefinition.class);
        when(definition.getCode()).thenReturn("CHN001");
        when(definition.nodeCount()).thenReturn(2);
        when(definition.layerCount()).thenReturn(1);

        when(chainRepo.list(null, null)).thenReturn(List.of(chain));
        when(designRepo.get("DSN001")).thenReturn(design);
        when(chainDefinitionBuilder.build(anyString(), any(), anyString(), anyString()))
                .thenReturn(definition);
        when(chainValidator.validateAll(any())).thenReturn(true);
        doNothing().when(chainManager).reload(anyList());

        boolean result = chainLoader.loadAllChains();

        assertThat(result).isTrue();
        verify(adminClient).notifyChainSync(argThat(sync ->
                sync.getLoadedChains().contains("CHN001")
        ));
    }

    @Test
    void loadAllChainsNoChainsDoesNotNotify() {
        when(chainRepo.list(null, null)).thenReturn(List.of());

        boolean result = chainLoader.loadAllChains();

        assertThat(result).isTrue();
        verify(adminClient, never()).notifyChainSync(any());
    }

    @Test
    void resolveChainDisplayName_dbUnavailable_fallsBackToChainCode() {
        when(chainRepo.get("linear-test")).thenThrow(new org.springframework.jdbc.BadSqlGrammarException("test", null, null));

        assertThat(chainLoader.resolveChainDisplayName("linear-test")).isEqualTo("linear-test");
    }

    @Test
    void resolveChainDisplayName_chainNotFound_fallsBackToChainCode() {
        when(chainRepo.get("missing")).thenReturn(null);

        assertThat(chainLoader.resolveChainDisplayName("missing")).isEqualTo("missing");
    }
}
