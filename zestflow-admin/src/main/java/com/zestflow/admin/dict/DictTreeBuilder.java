package com.zestflow.admin.dict;

import com.zestflow.admin.model.entity.DictDataPO;
import com.zestflow.admin.model.vo.DictDataTreeVO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 字典数据树构建：优先 parent_id 同类型树；否则按 parent_type_code/parent_value 跨类型分组。
 */
public final class DictTreeBuilder {

    private DictTreeBuilder() {
    }

    public static List<DictDataTreeVO> build(
            String typeCode,
            List<DictDataPO> items,
            Map<String, String> parentTypeLabelLookup) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        boolean hasParentId = items.stream().anyMatch(i -> i.getParentId() != null);
        if (hasParentId) {
            return buildByParentId(typeCode, items);
        }

        boolean hasCrossParent = items.stream()
                .anyMatch(i -> StringUtils.hasText(i.getParentTypeCode()) && StringUtils.hasText(i.getParentValue()));
        if (hasCrossParent) {
            return buildByCrossTypeGroup(typeCode, items, parentTypeLabelLookup);
        }

        return items.stream()
                .sorted(dataComparator())
                .map(po -> toNode(po, false))
                .collect(Collectors.toList());
    }

    private static List<DictDataTreeVO> buildByParentId(String typeCode, List<DictDataPO> items) {
        Map<Long, DictDataTreeVO> nodeMap = new HashMap<>();
        for (DictDataPO po : items) {
            nodeMap.put(po.getId(), toNode(po, false));
        }

        List<DictDataTreeVO> roots = new ArrayList<>();
        for (DictDataPO po : items) {
            DictDataTreeVO node = nodeMap.get(po.getId());
            Long parentId = po.getParentId();
            if (parentId == null || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodeMap.get(parentId).getChildren().add(node);
            }
        }

        sortTree(roots);
        return roots;
    }

    private static List<DictDataTreeVO> buildByCrossTypeGroup(
            String typeCode,
            List<DictDataPO> items,
            Map<String, String> parentTypeLabelLookup) {

        Map<String, List<DictDataPO>> grouped = new LinkedHashMap<>();
        List<DictDataPO> ungrouped = new ArrayList<>();

        for (DictDataPO po : items) {
            if (StringUtils.hasText(po.getParentTypeCode()) && StringUtils.hasText(po.getParentValue())) {
                grouped.computeIfAbsent(po.getParentValue().trim(), k -> new ArrayList<>()).add(po);
            } else {
                ungrouped.add(po);
            }
        }

        List<DictDataTreeVO> roots = new ArrayList<>();

        for (DictDataPO po : ungrouped) {
            roots.add(toNode(po, false));
        }

        for (Map.Entry<String, List<DictDataPO>> entry : grouped.entrySet()) {
            String parentValue = entry.getKey();
            List<DictDataPO> children = entry.getValue();
            String parentTypeCode = children.get(0).getParentTypeCode();
            String label = parentTypeLabelLookup != null
                    ? parentTypeLabelLookup.getOrDefault(parentValue, parentValue)
                    : parentValue;

            DictDataTreeVO group = DictDataTreeVO.builder()
                    .id(null)
                    .nodeKey("group:" + parentTypeCode + ":" + parentValue)
                    .virtualNode(true)
                    .typeCode(typeCode)
                    .parentTypeCode(parentTypeCode)
                    .parentValue(parentValue)
                    .label(label)
                    .value(parentValue)
                    .status(1)
                    .children(children.stream()
                            .sorted(dataComparator())
                            .map(c -> toNode(c, false))
                            .collect(Collectors.toList()))
                    .build();
            roots.add(group);
        }

        roots.sort(Comparator
                .comparing((DictDataTreeVO n) -> Boolean.TRUE.equals(n.getVirtualNode()) ? 1 : 0)
                .thenComparing(n -> n.getSort() != null ? n.getSort() : 0));
        return roots;
    }

    private static void sortTree(List<DictDataTreeVO> nodes) {
        nodes.sort(nodeComparator());
        for (DictDataTreeVO node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortTree(node.getChildren());
            }
        }
    }

    private static Comparator<DictDataPO> dataComparator() {
        return Comparator
                .comparing((DictDataPO d) -> d.getSort() != null ? d.getSort() : 0)
                .thenComparing(d -> d.getDefaultFlag() != null ? -d.getDefaultFlag() : 0)
                .thenComparing(DictDataPO::getId, Comparator.nullsLast(Long::compareTo));
    }

    private static Comparator<DictDataTreeVO> nodeComparator() {
        return Comparator
                .comparing((DictDataTreeVO n) -> n.getSort() != null ? n.getSort() : 0)
                .thenComparing(n -> n.getDefaultFlag() != null ? -n.getDefaultFlag() : 0)
                .thenComparing(n -> n.getId() != null ? n.getId() : 0L);
    }

    private static DictDataTreeVO toNode(DictDataPO po, boolean virtual) {
        return DictDataTreeVO.builder()
                .id(po.getId())
                .nodeKey(String.valueOf(po.getId()))
                .virtualNode(virtual)
                .typeCode(po.getTypeCode())
                .parentId(po.getParentId())
                .parentTypeCode(po.getParentTypeCode())
                .parentValue(po.getParentValue())
                .label(po.getLabel())
                .value(po.getValue())
                .sort(po.getSort())
                .status(po.getStatus())
                .tagType(po.getTagType())
                .defaultFlag(po.getDefaultFlag())
                .remark(po.getRemark())
                .extra(po.getExtra())
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .children(new ArrayList<>())
                .build();
    }

    /** 检测 parent_id 是否形成环 */
    public static boolean wouldCreateCycle(Map<Long, Long> parentMap, Long nodeId, Long newParentId) {
        if (newParentId == null || Objects.equals(nodeId, newParentId)) {
            return Objects.equals(nodeId, newParentId);
        }
        Long current = newParentId;
        int guard = 0;
        while (current != null && guard++ < parentMap.size() + 2) {
            if (Objects.equals(current, nodeId)) {
                return true;
            }
            current = parentMap.get(current);
        }
        return false;
    }
}
