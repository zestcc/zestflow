package com.zestflow.mcp.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code compose_chain} — 按平台 Pattern 模板实例化 production 链（非单节点占位）。
 */
public class ChainComposeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ComposeResult compose(String patternId, String chainCode, String chainName,
                                 Map<String, String> componentBindings) throws Exception {
        if (chainCode == null || chainCode.isBlank()) {
            throw new IllegalArgumentException("chainCode 不能为空");
        }
        ChainTemplate template = ChainTemplate.resolve(patternId, chainName);
        Map<String, String> bindings = componentBindings != null ? componentBindings : Map.of();

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        nodes.add(node("_start", "开始", "START", null));
        String previous = "_start";
        int idx = 1;
        for (TemplateStep step : template.steps()) {
            String nodeId = "n" + idx++;
            String component = bindings.getOrDefault(step.bindingKey(), step.defaultComponent());
            nodes.add(node(nodeId, step.label(), step.nodeType(), component));
            edges.add(edge(previous, nodeId));
            previous = nodeId;
        }
        nodes.add(node("_end", "结束", "END", null));
        edges.add(edge(previous, "_end"));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("lifecycle", "production");
        config.put("patternId", template.patternId());

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("code", chainCode);
        definition.put("version", 1);
        definition.put("nodes", nodes);
        definition.put("edges", edges);
        definition.put("config", config);

        String chainJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(definition);
        String graphJson = buildGraphData(template, nodes, edges);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("patternId", template.patternId());
        out.put("lifecycle", "production");
        out.put("chainDefinition", definition);
        out.put("chainDefinitionJson", chainJson);
        out.put("graphDataJson", graphJson);
        out.put("nodeCount", nodes.size());
        out.put("businessNodeCount", template.steps().size());
        out.put("nextSteps", List.of(
                "validate_chain(chainDefinitionJson)",
                "IDE Apply 链 JSON + graph_data 到 Admin 设计器",
                "gen_playground_scene + gen_smoke_suite",
                "validate_delivery(strictMode=true)"));
        return new ComposeResult(out);
    }

    private static Map<String, Object> node(String id, String label, String type, String component) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("label", label);
        n.put("type", type);
        if (component != null && !component.isBlank()) {
            n.put("component", component);
        }
        return n;
    }

    private static Map<String, Object> edge(String source, String target) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("source", source);
        e.put("target", target);
        return e;
    }

    private static String buildGraphData(ChainTemplate template, List<Map<String, Object>> nodes,
                                         List<Map<String, Object>> edges) throws Exception {
        List<Map<String, Object>> cells = new ArrayList<>();
        int y = 40;
        for (Map<String, Object> n : nodes) {
            String type = String.valueOf(n.getOrDefault("type", "NORMAL")).toUpperCase(Locale.ROOT);
            String shape = switch (type) {
                case "START" -> "flow-start";
                case "END" -> "flow-end";
                case "CONDITION" -> "flow-condition";
                case "LOADER" -> "flow-loader";
                case "PARSER" -> "flow-parser";
                default -> "flow-task";
            };
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("id", n.get("id"));
            cell.put("shape", shape);
            cell.put("position", Map.of("x", 180, "y", y));
            cell.put("size", Map.of("width", 160, "height", 46));
            cell.put("attrs", Map.of("label", Map.of("text", n.get("label"))));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("label", n.get("label"));
            data.put("nodeType", n.get("type"));
            if (n.get("component") != null) {
                data.put("componentId", n.get("component"));
            }
            cell.put("data", data);
            cells.add(cell);
            y += 90;
        }
        for (Map<String, Object> e : edges) {
            Map<String, Object> edgeCell = new LinkedHashMap<>();
            edgeCell.put("shape", "edge");
            edgeCell.put("source", Map.of("cell", e.get("source")));
            edgeCell.put("target", Map.of("cell", e.get("target")));
            cells.add(edgeCell);
        }
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("cells", cells);
        graph.put("patternId", template.patternId());
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(graph);
    }

    public record ComposeResult(Map<String, Object> payload) {
    }

    /** 平台内置 Pattern 清单（供 Admin Copilot / MCP 共用） */
    public static List<Map<String, String>> listPlatformPatterns() {
        return List.of(
                patternMeta("auth-owned-write", "归属鉴权写操作", "validate → load → authorize → mutate → sync"),
                patternMeta("guest-gated-read", "游客/登录门禁读", "loadMeta → gate → loadContent"),
                patternMeta("publish-workflow", "发布工作流", "validate → loadDraft → transform → persist → index"),
                patternMeta("paginated-list", "分页列表", "parseQuery → count → fetch → mapVo"),
                patternMeta("admin-decision", "审核决策", "loadAudit → validateTransition → apply → notify"),
                patternMeta("generic-crud", "通用 CRUD", "load → validate → exec → parse"));
    }

    private static Map<String, String> patternMeta(String id, String title, String topology) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("topology", topology);
        return m;
    }

    private record TemplateStep(String bindingKey, String defaultComponent, String label, String nodeType) {
    }

    private record ChainTemplate(String patternId, List<TemplateStep> steps) {
        static ChainTemplate resolve(String patternId, String chainName) {
            String id = patternId != null ? patternId.toLowerCase(Locale.ROOT).trim() : "";
            return switch (id) {
                case "auth-owned-write" -> new ChainTemplate("auth-owned-write", List.of(
                        step("validate", "validateRequest", "参数校验", "EXECUTOR"),
                        step("load", "loadResource", "加载资源", "LOADER"),
                        step("authorize", "authorizeOwner", "归属鉴权", "PREDICATE"),
                        step("mutate", "mutateResource", "写操作", "EXECUTOR"),
                        step("sync", "syncSideEffect", "副作用同步", "EXECUTOR")));
                case "guest-gated-read" -> new ChainTemplate("guest-gated-read", List.of(
                        step("loadMeta", "loadBookMeta", "加载元数据", "LOADER"),
                        step("gate", "checkPreviewOrAuth", "试读/登录门禁", "PREDICATE"),
                        step("loadContent", "loadChapterContent", "加载正文", "EXECUTOR")));
                case "publish-workflow" -> new ChainTemplate("publish-workflow", List.of(
                        step("validate", "validatePublishRequest", "发布校验", "EXECUTOR"),
                        step("loadDraft", "loadManuscriptDraft", "加载草稿", "LOADER"),
                        step("transform", "transformToBook", "转换书籍", "EXECUTOR"),
                        step("persist", "persistPublishedBook", "持久化", "EXECUTOR"),
                        step("index", "indexPublishedBook", "索引/通知", "EXECUTOR")));
                case "paginated-list" -> new ChainTemplate("paginated-list", List.of(
                        step("parseQuery", "parsePageQuery", "解析分页", "LOADER"),
                        step("count", "countRecords", "统计总数", "EXECUTOR"),
                        step("fetch", "fetchPageRecords", "分页查询", "EXECUTOR"),
                        step("mapVo", "mapToVoList", "映射 VO", "PARSER")));
                case "admin-decision" -> new ChainTemplate("admin-decision", List.of(
                        step("loadAudit", "loadAuditRecord", "加载审核单", "LOADER"),
                        step("validateTransition", "validateAuditTransition", "状态校验", "PREDICATE"),
                        step("apply", "applyAuditDecision", "执行决策", "EXECUTOR"),
                        step("notify", "notifyAuditResult", "通知", "EXECUTOR")));
                default -> new ChainTemplate("generic-crud", List.of(
                        step("load", "loadRequest", "解析入参", "LOADER"),
                        step("validate", "validateBusiness", "业务校验", "EXECUTOR"),
                        step("exec", "processBusiness", chainName != null ? chainName : "核心业务", "EXECUTOR"),
                        step("parse", "parseResponse", "响应解析", "PARSER")));
            };
        }

        private static TemplateStep step(String key, String defaultComponent, String label, String nodeType) {
            return new TemplateStep(key, defaultComponent, label, nodeType);
        }
    }
}
