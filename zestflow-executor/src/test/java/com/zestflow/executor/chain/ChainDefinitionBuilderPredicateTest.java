package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainNodeDTO;
import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ChainDefinitionBuilderPredicateTest {

    @Mock
    private ComponentScanner componentScanner;

    @Test
    void buildParsesInlineScriptPredicateConfig() {
        ChainDefinitionBuilder builder = new ChainDefinitionBuilder(componentScanner);
        ChainNodeDTO nodeDTO = ChainNodeDTO.builder()
                .id("cond1")
                .label("hasSupplier")
                .type("CONDITION")
                .component("INLINE_PRED_TEST")
                .componentName("hasSupplier")
                .config(Map.of(
                        "predicateMode", "script",
                        "predicateScript", "StringUtils.hasText(supplierType)",
                        "trueLabel", "Yes",
                        "falseLabel", "No"
                ))
                .build();
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code("CHN_PRED")
                .nodes(List.of(nodeDTO))
                .edges(List.of())
                .build();

        ChainDefinition def = builder.build(dto);
        NodeDefinition cond = def.getNode("cond1");

        assertThat(cond.getType()).isEqualTo(ChainConstants.NODE_TYPE_CONDITION);
        assertThat(cond.isInlineScriptPredicate()).isTrue();
        assertThat(cond.getPredicateScript()).isEqualTo("StringUtils.hasText(supplierType)");
        assertThat(cond.getTrueLabel()).isEqualTo("Yes");
        assertThat(cond.getFalseLabel()).isEqualTo("No");
    }
}
