package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.NodeResultDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChainFinalResultResolverTest {

    @Test
    void resolveReturnsLastSuccessfulReturnValue() {
        List<NodeResultDTO> results = List.of(
                NodeResultDTO.builder().nodeId("n1").status(ChainConstants.NODE_SUCCESS).returnValue("first").build(),
                NodeResultDTO.builder().nodeId("n2").status(ChainConstants.NODE_FAILED).returnValue("skip").build(),
                NodeResultDTO.builder().nodeId("n3").status(ChainConstants.NODE_SUCCESS).returnValue("<xml/>").build()
        );
        assertThat(ChainFinalResultResolver.resolve(results)).isEqualTo("<xml/>");
    }

    @Test
    void findFirstFailureReturnsEarliestFailedNode() {
        List<NodeResultDTO> results = List.of(
                NodeResultDTO.builder().nodeId("n1").status(ChainConstants.NODE_SUCCESS).build(),
                NodeResultDTO.builder().nodeId("n2").status(ChainConstants.NODE_FAILED).errorCode("E001").build()
        );
        NodeResultDTO failure = ChainFinalResultResolver.findFirstFailure(results);
        assertThat(failure.getNodeId()).isEqualTo("n2");
        assertThat(failure.getErrorCode()).isEqualTo("E001");
    }
}
