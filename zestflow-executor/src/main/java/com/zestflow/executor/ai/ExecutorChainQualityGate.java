package com.zestflow.executor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 链质量门禁（与 Admin {@link com.zestflow.admin.ai.AiChainQualityGate} 同源规则）。
 */
public final class ExecutorChainQualityGate {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MULTI_STEP_INTENT = Pattern.compile(
            "注册|登录|下单|订单|支付|退款|审批|流程|链路|创建|更新|删除|同步|导入|导出|"
                    + "register|login|order|payment|refund|approval|workflow|crud|pipeline",
            Pattern.CASE_INSENSITIVE);

    private ExecutorChainQualityGate() {
    }

    public static QualityResult assess(String userMessage, String chainDataJson) {
        if (!StringUtils.hasText(userMessage) || !StringUtils.hasText(chainDataJson)) {
            return QualityResult.ok();
        }
        if (!impliesMultiStepCapability(userMessage)) {
            return QualityResult.ok();
        }
        int businessNodes = countBusinessNodes(chainDataJson);
        if (businessNodes <= 2) {
            return QualityResult.reject(
                    "业务链过于简化（仅 " + businessNodes + " 个业务节点）。"
                            + "请对标业界主路径拆分解析、校验、分支、核心操作、响应等独立节点。");
        }
        if (isBlackBoxChain(chainDataJson, userMessage)) {
            return QualityResult.reject(
                    "检测到「单节点黑盒」。请按验收标准拆步，每节点单一职责。");
        }
        if (!hasBranchWhenNeeded(chainDataJson) && businessNodes <= 4) {
            return QualityResult.reject(
                    "该类业务通常需要 CONDITION 分叉或失败路径，请补全分支与错误响应节点。");
        }
        return QualityResult.ok();
    }

    static boolean impliesMultiStepCapability(String userMessage) {
        return MULTI_STEP_INTENT.matcher(userMessage).find();
    }

    static int countBusinessNodes(String chainDataJson) {
        try {
            JsonNode nodes = MAPPER.readTree(chainDataJson).path("nodes");
            if (!nodes.isArray()) {
                return 0;
            }
            int count = 0;
            for (JsonNode node : nodes) {
                String type = node.path("type").asText("NORMAL").toUpperCase(Locale.ROOT);
                if ("START".equals(type) || "END".equals(type)) {
                    continue;
                }
                count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    static boolean isBlackBoxChain(String chainDataJson, String userMessage) {
        try {
            JsonNode nodes = MAPPER.readTree(chainDataJson).path("nodes");
            if (!nodes.isArray() || nodes.size() != 1) {
                return false;
            }
            String label = nodes.get(0).path("label").asText("");
            if (!StringUtils.hasText(label) || label.length() < 2) {
                return false;
            }
            String normalizedMsg = normalize(userMessage);
            String normalizedLabel = normalize(label);
            return normalizedMsg.contains(normalizedLabel) || normalizedLabel.length() >= 4
                    && normalizedMsg.contains(normalizedLabel.substring(0, Math.min(4, normalizedLabel.length())));
        } catch (Exception e) {
            return false;
        }
    }

    static boolean hasBranchWhenNeeded(String chainDataJson) {
        try {
            JsonNode root = MAPPER.readTree(chainDataJson);
            JsonNode nodes = root.path("nodes");
            if (!nodes.isArray()) {
                return false;
            }
            for (JsonNode node : nodes) {
                if ("CONDITION".equalsIgnoreCase(node.path("type").asText())) {
                    return true;
                }
            }
            JsonNode edges = root.path("edges");
            if (!edges.isArray()) {
                return false;
            }
            Map<String, Integer> outDegree = new HashMap<>();
            for (JsonNode edge : edges) {
                String source = edge.path("source").asText();
                outDegree.merge(source, 1, Integer::sum);
            }
            return outDegree.values().stream().anyMatch(d -> d >= 2);
        } catch (Exception e) {
            return true;
        }
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT)
                .replace("链", "")
                .replace("流程", "");
    }

    public record QualityResult(boolean accepted, String critique) {
        static QualityResult ok() {
            return new QualityResult(true, "");
        }

        static QualityResult reject(String critique) {
            return new QualityResult(false, critique);
        }
    }
}
