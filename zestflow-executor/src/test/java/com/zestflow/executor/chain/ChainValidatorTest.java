package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChainValidatorTest {

    @Mock
    private ComponentScanner componentScanner;

    private ChainValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ChainValidator(componentScanner);
    }

    @Test
    void validateNullDefinition() {
        List<String> errors = validator.validate(null);
        assertThat(errors).contains("链定义为 null");
    }

    @Test
    void validateNoCode() {
        ChainDefinition def = ChainDefinition.builder()
                .code(null)
                .nodes(Map.of("n1", buildNodeDef("n1", "comp", ChainConstants.NODE_TYPE_NORMAL)))
                .build();

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("编码不能为空"));
    }

    @Test
    void validateNoNodes() {
        ChainDefinition def = ChainDefinition.builder().code("chain-1").build();

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("没有节点"));
    }

    @Test
    void validateValidChain() {
        when(componentScanner.getComponent("comp-1")).thenReturn(new ComponentScanner.ComponentMeta());
        when(componentScanner.getComponent("comp-2")).thenReturn(new ComponentScanner.ComponentMeta());

        ChainDefinition def = buildValidDefinition();

        List<String> errors = validator.validate(def);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateComponentNotFound() {
        ChainDefinition def = buildValidDefinition();
        // comp-1 not registered with scanner
        when(componentScanner.getComponentIds()).thenReturn(Set.of("existing-comp"));

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("不存在的组件"));
    }

    @Test
    void validateEdgeRefersToNonExistentSource() {
        ChainDefinition def = buildValidDefinition();
        def.setEdges(List.of(
                new ChainDefinition.ChainEdge("non-existent-source", "node-2", null, null)
        ));

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("不存在的源节点"));
    }

    @Test
    void validateEdgeRefersToNonExistentTarget() {
        ChainDefinition def = buildValidDefinition();
        def.setEdges(List.of(
                new ChainDefinition.ChainEdge("node-1", "non-existent-target", null, null)
        ));

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("不存在的目标节点"));
    }

    @Test
    void detectCycle() {
        NodeDefinition nodeA = buildNodeDef("A", "comp", ChainConstants.NODE_TYPE_NORMAL);
        NodeDefinition nodeB = buildNodeDef("B", "comp", ChainConstants.NODE_TYPE_NORMAL);
        NodeDefinition nodeC = buildNodeDef("C", "comp", ChainConstants.NODE_TYPE_NORMAL);

        Map<String, List<String>> adj = new HashMap<>();
        adj.put("A", List.of("B"));
        adj.put("B", List.of("C"));
        adj.put("C", List.of("A")); // A ← C creates cycle: A → B → C → A

        ChainDefinition def = ChainDefinition.builder()
                .code("cycle-chain")
                .nodes(Map.of("A", nodeA, "B", nodeB, "C", nodeC))
                .edges(List.of(
                        new ChainDefinition.ChainEdge("A", "B", null, null),
                        new ChainDefinition.ChainEdge("B", "C", null, null),
                        new ChainDefinition.ChainEdge("C", "A", null, null)
                ))
                .adjacency(adj)
                .build();

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("存在环路"));
    }

    @Test
    void hasCycleReturnsFalse() {
        NodeDefinition nodeA = buildNodeDef("A", "comp", ChainConstants.NODE_TYPE_NORMAL);
        NodeDefinition nodeB = buildNodeDef("B", "comp", ChainConstants.NODE_TYPE_NORMAL);

        Map<String, List<String>> adj = new HashMap<>();
        adj.put("A", List.of("B"));

        ChainDefinition def = ChainDefinition.builder()
                .code("acyclic")
                .nodes(Map.of("A", nodeA, "B", nodeB))
                .edges(List.of(new ChainDefinition.ChainEdge("A", "B", null, null)))
                .adjacency(adj)
                .build();

        assertThat(validator.hasCycle(def)).isFalse();
    }

    @Test
    void hasCycleDetectsCycle() {
        NodeDefinition nodeA = buildNodeDef("A", "comp", ChainConstants.NODE_TYPE_NORMAL);
        NodeDefinition nodeB = buildNodeDef("B", "comp", ChainConstants.NODE_TYPE_NORMAL);

        Map<String, List<String>> adj = new HashMap<>();
        adj.put("A", List.of("B"));
        adj.put("B", List.of("A")); // B → A creates cycle

        ChainDefinition def = ChainDefinition.builder()
                .code("cyclic")
                .nodes(Map.of("A", nodeA, "B", nodeB))
                .edges(List.of(
                        new ChainDefinition.ChainEdge("A", "B", null, null),
                        new ChainDefinition.ChainEdge("B", "A", null, null)
                ))
                .adjacency(adj)
                .build();

        assertThat(validator.hasCycle(def)).isTrue();
    }

    @Test
    void validateNegativeTimeout() {
        when(componentScanner.getComponent("comp-1")).thenReturn(new ComponentScanner.ComponentMeta());

        NodeDefinition node = buildNodeDef("node-1", "comp-1", ChainConstants.NODE_TYPE_NORMAL);
        node.setTimeout(-5);

        ChainDefinition def = ChainDefinition.builder()
                .code("chain-1")
                .nodes(Map.of("node-1", node))
                .adjacency(new HashMap<>())
                .build();

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("超时时间不能为负数"));
    }

    @Test
    void validateNegativeRetryCount() {
        when(componentScanner.getComponent("comp-1")).thenReturn(new ComponentScanner.ComponentMeta());

        NodeDefinition node = buildNodeDef("node-1", "comp-1", ChainConstants.NODE_TYPE_NORMAL);
        node.setRetryCount(-1);

        ChainDefinition def = ChainDefinition.builder()
                .code("chain-1")
                .nodes(Map.of("node-1", node))
                .adjacency(new HashMap<>())
                .build();

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("重试次数不能为负数"));
    }

    @Test
    void validateEmptyNodeType() {
        when(componentScanner.getComponent("comp-1")).thenReturn(new ComponentScanner.ComponentMeta());

        NodeDefinition node = buildNodeDef("node-1", "comp-1", "");
        ChainDefinition def = ChainDefinition.builder()
                .code("chain-1")
                .nodes(Map.of("node-1", node))
                .adjacency(new HashMap<>())
                .build();

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("类型不能为空"));
    }

    @Test
    void validateAllPass() {
        when(componentScanner.getComponent("comp-1")).thenReturn(new ComponentScanner.ComponentMeta());
        when(componentScanner.getComponent("comp-2")).thenReturn(new ComponentScanner.ComponentMeta());

        boolean result = validator.validateAll(List.of(buildValidDefinition()));
        assertThat(result).isTrue();
    }

    @Test
    void validateAllFail() {
        ChainDefinition def = ChainDefinition.builder().build();

        boolean result = validator.validateAll(List.of(def));
        assertThat(result).isFalse();
    }

    @Test
    void validateMissingComponentOnNormalNode() {
        NodeDefinition node = buildNodeDef("node-1", null, ChainConstants.NODE_TYPE_NORMAL);
        ChainDefinition def = ChainDefinition.builder()
                .code("chain-1")
                .nodes(Map.of("node-1", node))
                .adjacency(new HashMap<>())
                .build();

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("缺少 component 配置"));
    }

    @Test
    void validateMissingComponentOnConditionNode() {
        NodeDefinition node = buildNodeDef("node-1", null, ChainConstants.NODE_TYPE_CONDITION);
        ChainDefinition def = ChainDefinition.builder()
                .code("chain-1")
                .nodes(Map.of("node-1", node))
                .adjacency(new HashMap<>())
                .build();

        List<String> errors = validator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("缺少 component 配置"));
    }

    @Test
    void validateStartEndNodesSkipComponentCheck() {
        NodeDefinition start = buildNodeDef("start", null, ChainConstants.NODE_TYPE_NORMAL);
        start.setType("start");
        NodeDefinition end = buildNodeDef("end", null, ChainConstants.NODE_TYPE_NORMAL);
        end.setType("end");
        ChainDefinition def = ChainDefinition.builder()
                .code("chain-1")
                .nodes(Map.of("start", start, "end", end))
                .adjacency(new HashMap<>())
                .build();

        List<String> errors = validator.validate(def);
        assertThat(errors).noneMatch(e -> e.contains("component"));
    }

    private ChainDefinition buildValidDefinition() {
        NodeDefinition node1 = buildNodeDef("node-1", "comp-1", ChainConstants.NODE_TYPE_NORMAL);
        NodeDefinition node2 = buildNodeDef("node-2", "comp-2", ChainConstants.NODE_TYPE_NORMAL);

        Map<String, List<String>> adj = new HashMap<>();
        adj.put("node-1", List.of("node-2"));

        return ChainDefinition.builder()
                .code("valid-chain")
                .nodes(Map.of("node-1", node1, "node-2", node2))
                .edges(List.of(new ChainDefinition.ChainEdge("node-1", "node-2", null, null)))
                .adjacency(adj)
                .build();
    }

    private NodeDefinition buildNodeDef(String id, String component, String type) {
        return NodeDefinition.builder()
                .id(id)
                .component(component)
                .type(type)
                .timeout(-1)
                .retryCount(0)
                .build();
    }
}
