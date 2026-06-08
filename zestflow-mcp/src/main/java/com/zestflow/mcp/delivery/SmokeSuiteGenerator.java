package com.zestflow.mcp.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code gen_smoke_suite} — 扫描 @ZestChain + Controller 路由，生成 acceptance journeys 草稿。
 */
public class SmokeSuiteGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping(?:\\(\\s*\"([^\"]*)\"\\s*\\)|\\(\\s*value\\s*=\\s*\"([^\"]*)\"\\s*\\)|\\(\\))");
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\(\"([^\"]*)\"\\)");
    private static final Pattern ZEST_CHAIN = Pattern.compile(
            "@ZestChain\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"\\)");

    public String generate(Path projectRoot) throws IOException {
        List<Map<String, Object>> journeys = new ArrayList<>();
        Set<String> chainKeys = ProjectSourceAnalyzer.scanZestChainKeys(projectRoot);

        journeys.add(guestBrowseTemplate());
        journeys.add(authRequiredTemplate());

        List<Map<String, Object>> autoSteps = scanControllerEndpoints(projectRoot);
        if (!autoSteps.isEmpty()) {
            Map<String, Object> auto = new LinkedHashMap<>();
            auto.put("id", "auto_chain_smoke");
            auto.put("description", "由 @ZestChain Controller 自动生成的基础冒烟（需补充断言与 fixture）");
            auto.put("steps", autoSteps);
            journeys.add(auto);
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", 1);
        doc.put("generatedBy", "zestflow-mcp/gen_smoke_suite");
        doc.put("chainKeyCount", chainKeys.size());
        doc.put("journeys", journeys);
        doc.put("hints", List.of(
                "补充 expect.status / expect.body 字段",
                "游客/登录/403 边界各至少一条",
                "完成后 run_acceptance_suite → validate_delivery"));

        String yamlLike = toYamlLike(doc);
        Path outDir = projectRoot.resolve(".zestflow/acceptance");
        Files.createDirectories(outDir);
        Path target = outDir.resolve("journeys.yml");
        Files.writeString(target, yamlLike, StandardCharsets.UTF_8);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("written", projectRoot.relativize(target).toString().replace('\\', '/'));
        result.put("journeyCount", journeys.size());
        result.put("chainKeyCount", chainKeys.size());
        result.put("next", "run_acceptance_suite → validate_delivery(strictMode=true)");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private static Map<String, Object> guestBrowseTemplate() {
        Map<String, Object> journey = new LinkedHashMap<>();
        journey.put("id", "guest_public_read");
        journey.put("description", "游客公开读接口（200）");
        journey.put("auth", "none");
        journey.put("steps", List.of(
                step("GET", "/api/health-or-list", 200, Map.of()),
                step("GET", "/api/resource/{id}", 200, Map.of("note", "替换为实际公开路由"))));
        return journey;
    }

    private static Map<String, Object> authRequiredTemplate() {
        Map<String, Object> journey = new LinkedHashMap<>();
        journey.put("id", "auth_forbidden_boundary");
        journey.put("description", "未登录访问受保护写接口（403/401）");
        journey.put("auth", "none");
        journey.put("steps", List.of(
                step("PUT", "/api/protected/resource", 403, Map.of("note", "替换为实际受保护路由"))));
        return journey;
    }

    private List<Map<String, Object>> scanControllerEndpoints(Path projectRoot) throws IOException {
        List<Map<String, Object>> steps = new ArrayList<>();
        Files.walk(projectRoot)
                .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                .filter(p -> !p.toString().contains("target"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        if (!content.contains("@ZestChain")) {
                            return;
                        }
                        String base = "";
                        Matcher classM = CLASS_MAPPING.matcher(content);
                        if (classM.find()) {
                            base = classM.group(1);
                        }
                        String[] lines = content.split("\n");
                        for (int i = 0; i < lines.length; i++) {
                            if (!lines[i].contains("@ZestChain")) {
                                continue;
                            }
                            String chainKey = extractChainKey(lines[i]);
                            for (int j = Math.max(0, i - 6); j <= Math.min(lines.length - 1, i + 2); j++) {
                                Matcher m = MAPPING.matcher(lines[j]);
                                if (m.find()) {
                                    String sub = m.group(2) != null ? m.group(2) : (m.group(3) != null ? m.group(3) : "");
                                    String method = m.group(1).toUpperCase(Locale.ROOT);
                                    String path = (base + sub).replace("//", "/");
                                    Map<String, Object> expect = new LinkedHashMap<>();
                                    expect.put("chainKey", chainKey);
                                    steps.add(step(method, path, 200, expect));
                                    break;
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        // skip file
                    }
                });
        return steps.size() > 20 ? steps.subList(0, 20) : steps;
    }

    private static String extractChainKey(String line) {
        Matcher m = ZEST_CHAIN.matcher(line);
        return m.find() ? m.group(1) : "unknown";
    }

    private static Map<String, Object> step(String method, String path, int status, Map<String, Object> extra) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("method", method);
        step.put("path", path);
        step.put("expect", Map.of("status", status));
        step.putAll(extra);
        return step;
    }

    @SuppressWarnings("unchecked")
    private static String toYamlLike(Map<String, Object> doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ZestFlow Acceptance Journeys — 由 gen_smoke_suite 生成，请按业务补充断言\n");
        sb.append("version: ").append(doc.get("version")).append('\n');
        sb.append("journeys:\n");
        List<Map<String, Object>> journeys = (List<Map<String, Object>>) doc.get("journeys");
        for (Map<String, Object> journey : journeys) {
            sb.append("  - id: ").append(journey.get("id")).append('\n');
            sb.append("    description: \"").append(journey.get("description")).append("\"\n");
            if (journey.containsKey("auth")) {
                sb.append("    auth: ").append(journey.get("auth")).append('\n');
            }
            sb.append("    steps:\n");
            List<Map<String, Object>> steps = (List<Map<String, Object>>) journey.get("steps");
            for (Map<String, Object> step : steps) {
                sb.append("      - method: ").append(step.get("method")).append('\n');
                sb.append("        path: \"").append(step.get("path")).append("\"\n");
                Object expect = step.get("expect");
                if (expect instanceof Map<?, ?> expectMap) {
                    sb.append("        expect:\n");
                    expectMap.forEach((k, v) -> sb.append("          ").append(k).append(": ")
                            .append(formatYamlValue(v)).append('\n'));
                }
            }
        }
        return sb.toString();
    }

    private static String formatYamlValue(Object v) {
        if (v instanceof String s) {
            return s.contains(" ") ? "\"" + s + "\"" : s;
        }
        return String.valueOf(v);
    }
}
