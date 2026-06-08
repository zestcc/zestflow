package com.zestflow.executor.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorChainProposalParserTest {

    @Test
    void parse_extractsChainDataFromJsonObject() {
        String reply = """
                {"summary":"注册链","chainData":{"nodes":[{"id":"a"}],"edges":[]}}
                """;
        var proposal = ExecutorChainProposalParser.parse(reply);
        assertTrue(proposal.chainData().contains("\"nodes\""));
        assertEquals("注册链", proposal.summary());
    }

    @Test
    void parse_stripsMarkdownFence() {
        String reply = """
                ```json
                {"nodes":[{"id":"x"}],"edges":[]}
                ```
                """;
        var proposal = ExecutorChainProposalParser.parse(reply);
        assertTrue(proposal.chainData().contains("x"));
    }

    @Test
    void stripMarkdownJson_handlesPlainJson() {
        assertEquals("{\"a\":1}", ExecutorChainProposalParser.stripMarkdownJson("{\"a\":1}"));
    }
}
