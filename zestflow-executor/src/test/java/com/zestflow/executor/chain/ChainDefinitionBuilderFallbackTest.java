package com.zestflow.executor.chain;

import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ChainDefinitionBuilderFallbackTest {

    @Mock
    private ComponentScanner componentScanner;

    @Test
    void build_shouldFallbackToGraphData_whenChainDataHasNoNodes() {
        ChainDefinitionBuilder builder = new ChainDefinitionBuilder(componentScanner);
        String graphData = """
                {"nodes":[{"id":"n1","label":"E2E","type":"SCRIPT","script":"return [ok:true]"}],"edges":[]}
                """;
        String chainData = "{\"version\":1,\"entryNodeId\":\"n1\"}";

        ChainDefinition def = builder.build("CHN_E2E", 1, chainData, graphData);

        assertThat(def.getCode()).isEqualTo("CHN_E2E");
        assertThat(def.nodeCount()).isEqualTo(1);
        assertThat(def.getNodes()).containsKey("n1");
    }
}
