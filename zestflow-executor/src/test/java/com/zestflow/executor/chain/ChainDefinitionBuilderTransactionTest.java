package com.zestflow.executor.chain;

import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainNodeDTO;
import com.zestflow.common.protocol.ChainTransactionConfig;
import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ChainDefinitionBuilderTransactionTest {

    @Mock
    private ComponentScanner componentScanner;

    @Test
    void buildParsesChainLevelTransactionConfig() {
        ChainDefinitionBuilder builder = new ChainDefinitionBuilder(componentScanner);
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code("CHN_TX")
                .config(Map.of("transaction", Map.of(
                        "enabled", true,
                        "propagation", "REQUIRED"
                )))
                .nodes(List.of())
                .edges(List.of())
                .build();

        ChainDefinition def = builder.build(dto);

        assertThat(def.isTransactionEnabled()).isTrue();
        assertThat(def.getTransactionConfig().getPropagation()).isEqualTo("REQUIRED");
    }

    @Test
    void buildParsesNodeTransactionPropagation() {
        ChainDefinitionBuilder builder = new ChainDefinitionBuilder(componentScanner);
        ChainNodeDTO nodeDTO = ChainNodeDTO.builder()
                .id("task1")
                .label("task")
                .type("NORMAL")
                .component("demoTask")
                .config(Map.of("transactionPropagation", "REQUIRES_NEW"))
                .build();
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code("CHN_TX_NODE")
                .nodes(List.of(nodeDTO))
                .edges(List.of())
                .build();

        ChainDefinition def = builder.build(dto);

        assertThat(def.getNode("task1").getTransactionPropagation()).isEqualTo("REQUIRES_NEW");
    }
}
