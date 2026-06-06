package com.zestflow.admin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.model.vo.AiChainKeyHintsVO;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.service.ExecutorRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 对比 Executor @ZestChain 声明与 Admin 链列表中的 chain_key，供 Copilot 提示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChainKeyHintService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorRegistryService executorRegistryService;
    private final ExecutorProxyService executorProxyService;

    public AiChainKeyHintsVO getHints(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return AiChainKeyHintsVO.builder()
                    .declaredKeys(List.of())
                    .adminKeys(List.of())
                    .declaredNotInAdmin(List.of())
                    .adminNotDeclared(List.of())
                    .build();
        }
        Set<String> declared = new TreeSet<>(executorRegistryService.listDeclaredChainKeysByApp(appCode));
        Set<String> adminKeys = fetchAdminChainKeys(appCode);

        List<String> declaredNotInAdmin = declared.stream()
                .filter(k -> !adminKeys.contains(k))
                .toList();
        List<String> adminNotDeclared = adminKeys.stream()
                .filter(k -> !declared.contains(k))
                .toList();

        return AiChainKeyHintsVO.builder()
                .declaredKeys(new ArrayList<>(declared))
                .adminKeys(new ArrayList<>(adminKeys))
                .declaredNotInAdmin(declaredNotInAdmin)
                .adminNotDeclared(adminNotDeclared)
                .build();
    }

    private Set<String> fetchAdminChainKeys(String appCode) {
        Set<String> keys = new LinkedHashSet<>();
        try {
            String json = executorProxyService.getFromExecutor(appCode, "/api/chains", "?page=1&size=9999");
            if (!StringUtils.hasText(json)) {
                return keys;
            }
            JsonNode root = MAPPER.readTree(json);
            JsonNode records = root.has("records") ? root.get("records") : root;
            if (records.isArray()) {
                for (JsonNode row : records) {
                    String chainKey = row.path("chainKey").asText("");
                    if (StringUtils.hasText(chainKey)) {
                        keys.add(chainKey.trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取 Admin 链 chain_key 失败 appCode={}", appCode, e);
        }
        return keys;
    }
}
