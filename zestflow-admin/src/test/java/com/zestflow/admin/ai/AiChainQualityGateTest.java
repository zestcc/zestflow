package com.zestflow.admin.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiChainQualityGateTest {

    @Test
    void fail_singleNodeRegisterBlackBox() {
        String chain = "{\"nodes\":[{\"id\":\"r\",\"label\":\"用户注册\",\"type\":\"NORMAL\",\"component\":\"registerUser\"}],\"edges\":[]}";
        AiChainQualityGate.QualityResult result = AiChainQualityGate.assess("帮我做用户注册链", chain);
        assertThat(result.accepted()).isFalse();
        assertThat(result.critique()).isNotBlank();
    }

    @Test
    void pass_whenEnoughNodes() {
        String chain = "{\"nodes\":["
                + "{\"id\":\"a\",\"type\":\"NORMAL\"},{\"id\":\"b\",\"type\":\"NORMAL\"},"
                + "{\"id\":\"c\",\"type\":\"CONDITION\"},{\"id\":\"d\",\"type\":\"NORMAL\"}"
                + "],\"edges\":[{\"source\":\"c\",\"target\":\"d\",\"label\":\"True\"}]}";
        AiChainQualityGate.QualityResult result = AiChainQualityGate.assess("用户注册", chain);
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void pass_forNonMultiStepIntent() {
        String chain = "{\"nodes\":[{\"id\":\"x\",\"type\":\"NORMAL\"}],\"edges\":[]}";
        assertThat(AiChainQualityGate.assess("解释这个链", chain).accepted()).isTrue();
    }
}
