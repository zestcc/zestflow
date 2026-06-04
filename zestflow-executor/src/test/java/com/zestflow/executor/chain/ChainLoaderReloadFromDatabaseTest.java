package com.zestflow.executor.chain;

import com.zestflow.executor.design.DesignPO;
import com.zestflow.executor.design.DesignRepository;
import com.zestflow.executor.design.DesignStatus;
import com.zestflow.executor.engine.NodeRunner;
import com.zestflow.executor.registry.AdminClient;
import com.zestflow.executor.registry.ExecutorProperties;
import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChainLoaderReloadFromDatabaseTest {

    @Mock ChainManager chainManager;
    @Mock ComponentScanner componentScanner;
    @Mock ChainValidator chainValidator;
    @Mock ChainDefinitionBuilder chainDefinitionBuilder;
    @Mock ChainRepository chainRepo;
    @Mock DesignRepository designRepo;
    @Mock NodeRunner nodeRunner;
    @Mock AdminClient adminClient;
    @Mock ExecutorProperties executorProperties;
    @Mock ObjectProvider<com.zestflow.executor.route.ChainRouteRegistry> chainRouteRegistryProvider;

    @Test
    void reloadFromDatabase_doesNotIncrementVersionOrNotifyAdmin() throws Exception {
        ChainPO chain = ChainPO.builder()
                .code("CHN001").status(4).designCode("DSN001").version(1).build();
        DesignPO design = DesignPO.builder()
                .code("DSN001").status(DesignStatus.ENABLED)
                .graphData("{\"nodes\":[]}").chainData("{\"version\":1}").build();
        ChainDefinition definition = mock(ChainDefinition.class);
        when(definition.getCode()).thenReturn("CHN001");
        when(definition.nodeCount()).thenReturn(1);

        when(chainRepo.get("CHN001")).thenReturn(chain);
        when(designRepo.get("DSN001")).thenReturn(design);
        when(chainDefinitionBuilder.build(eq("CHN001"), anyInt(), anyString(), anyString()))
                .thenReturn(definition);
        when(chainValidator.validate(definition)).thenReturn(List.of());

        ChainLoader loader = new ChainLoader(chainManager, componentScanner, chainValidator,
                chainDefinitionBuilder, chainRepo, designRepo, nodeRunner, adminClient, executorProperties,
                chainRouteRegistryProvider);

        ChainLoader.ChainReloadResult result = loader.reloadFromDatabase("CHN001");

        assertThat(result.isSuccess()).isTrue();
        verify(chainRepo, never()).incrementVersion(anyString());
        verify(adminClient, never()).notifyChainSync(any());
    }
}
