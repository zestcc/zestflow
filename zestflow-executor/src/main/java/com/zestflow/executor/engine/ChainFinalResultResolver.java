package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.NodeResultDTO;

import java.util.List;

/**
 * 链终态返回值解析 — 取拓扑执行序中最后一个成功节点的 {@code returnValue}（通常为 PARSER 输出）。
 */
public final class ChainFinalResultResolver {

    private ChainFinalResultResolver() {
    }

    public static Object resolve(List<NodeResultDTO> nodeResults) {
        if (nodeResults == null || nodeResults.isEmpty()) {
            return null;
        }
        for (int i = nodeResults.size() - 1; i >= 0; i--) {
            NodeResultDTO r = nodeResults.get(i);
            if (r.getStatus() != null && r.getStatus() == ChainConstants.NODE_SUCCESS && r.getReturnValue() != null) {
                return r.getReturnValue();
            }
        }
        return null;
    }

    public static NodeResultDTO findFirstFailure(List<NodeResultDTO> nodeResults) {
        if (nodeResults == null) {
            return null;
        }
        for (NodeResultDTO r : nodeResults) {
            if (r == null || r.getStatus() == null) {
                continue;
            }
            if (r.getStatus() == ChainConstants.NODE_FAILED || r.getStatus() == ChainConstants.NODE_TIMEOUT) {
                return r;
            }
        }
        return null;
    }
}
