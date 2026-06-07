package com.zestflow.admin.dict;

import com.zestflow.admin.model.entity.DictDataPO;
import com.zestflow.admin.model.vo.DictDataTreeVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DictTreeBuilderTest {

    @Test
    void buildByParentId_nestedTree() {
        List<DictDataPO> items = List.of(
                po(1L, null, "root", "root"),
                po(2L, 1L, "child", "child"),
                po(3L, 2L, "leaf", "leaf"));

        List<DictDataTreeVO> tree = DictTreeBuilder.build("region", items, Map.of());

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getChildren().get(0).getValue()).isEqualTo("leaf");
    }

    @Test
    void buildByCrossTypeGroup_virtualNodes() {
        DictDataPO model = po(10L, null, "DeepSeek Chat", "deepseek-chat");
        model.setParentTypeCode("ai_provider");
        model.setParentValue("deepseek");

        List<DictDataTreeVO> tree = DictTreeBuilder.build(
                "ai_model",
                List.of(model),
                Map.of("deepseek", "DeepSeek"));

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getVirtualNode()).isTrue();
        assertThat(tree.get(0).getLabel()).isEqualTo("DeepSeek");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getValue()).isEqualTo("deepseek-chat");
    }

    @Test
    void wouldCreateCycle_detectsLoop() {
        Map<Long, Long> parentMap = Map.of(
                2L, 1L,
                3L, 2L);
        assertThat(DictTreeBuilder.wouldCreateCycle(parentMap, 1L, 3L)).isTrue();
        assertThat(DictTreeBuilder.wouldCreateCycle(parentMap, 1L, 2L)).isTrue();
        assertThat(DictTreeBuilder.wouldCreateCycle(parentMap, 4L, 3L)).isFalse();
    }

    private static DictDataPO po(Long id, Long parentId, String label, String value) {
        DictDataPO po = new DictDataPO();
        po.setId(id);
        po.setTypeCode("test");
        po.setParentId(parentId);
        po.setLabel(label);
        po.setValue(value);
        po.setSort(1);
        po.setStatus(1);
        return po;
    }
}
