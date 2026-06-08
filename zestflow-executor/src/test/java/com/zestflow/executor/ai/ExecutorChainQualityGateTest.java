package com.zestflow.executor.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorChainQualityGateTest {

    @Test
    void rejectsBlackBoxSingleNodeForRegisterIntent() {
        String chain = """
                {"nodes":[{"id":"n1","type":"NORMAL","label":"用户注册"}],"edges":[]}
                """;
        var result = ExecutorChainQualityGate.assess("用户注册流程", chain);
        assertFalse(result.accepted());
    }

    @Test
    void acceptsMultiNodeChainWithCondition() {
        String chain = """
                {"nodes":[
                  {"id":"s","type":"START","label":"开始"},
                  {"id":"v","type":"NORMAL","label":"校验参数"},
                  {"id":"c","type":"CONDITION","label":"重复检查"},
                  {"id":"r","type":"NORMAL","label":"创建用户"},
                  {"id":"e","type":"END","label":"结束"}
                ],"edges":[
                  {"source":"s","target":"v"},
                  {"source":"v","target":"c"},
                  {"source":"c","target":"r","label":"False"},
                  {"source":"c","target":"e","label":"True"},
                  {"source":"r","target":"e"}
                ]}
                """;
        var result = ExecutorChainQualityGate.assess("用户注册", chain);
        assertTrue(result.accepted());
    }

    @Test
    void simpleGreeting_skipsStrictGate() {
        String chain = """
                {"nodes":[{"id":"n1","type":"NORMAL","label":"hello"}],"edges":[]}
                """;
        assertTrue(ExecutorChainQualityGate.assess("hello world", chain).accepted());
    }
}
