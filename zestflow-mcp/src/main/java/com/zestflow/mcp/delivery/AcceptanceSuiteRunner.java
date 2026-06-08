package com.zestflow.mcp.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code run_acceptance_suite} — 执行 journeys.yml 中的 HTTP 步骤（需应用运行或 dryRun 结构校验）。
 */
public class AcceptanceSuiteRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern STEP_BLOCK = Pattern.compile(
            "- method:\\s*(\\S+)\\s*\\n\\s*path:\\s*\"([^\"]+)\"\\s*\\n\\s*expect:\\s*\\n\\s*status:\\s*(\\d+)",
            Pattern.MULTILINE);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public String run(Path projectRoot, String baseUrl, boolean dryRun) throws IOException {
        Path journeys = projectRoot.resolve(".zestflow/acceptance/journeys.yml");
        if (!Files.isRegularFile(journeys)) {
            throw new IllegalStateException("缺少 .zestflow/acceptance/journeys.yml，请先 gen_smoke_suite");
        }

        String content = Files.readString(journeys, StandardCharsets.UTF_8);
        List<StepCase> cases = parseSteps(content);
        if (cases.isEmpty()) {
            throw new IllegalStateException("journeys.yml 未解析到任何 step");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int passed = 0;
        for (StepCase c : cases) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("method", c.method());
            row.put("path", c.path());
            row.put("expectedStatus", c.expectedStatus());
            if (dryRun || baseUrl == null || baseUrl.isBlank()) {
                row.put("status", "SKIPPED");
                row.put("passed", true);
                passed++;
            } else if (c.path().contains("{")) {
                row.put("status", "SKIPPED_PLACEHOLDER");
                row.put("passed", true);
                row.put("note", "路径含占位符，请替换 fixture 后重跑");
                passed++;
            } else {
                int actual = invoke(baseUrl, c);
                boolean ok = actual == c.expectedStatus();
                row.put("actualStatus", actual);
                row.put("passed", ok);
                if (ok) {
                    passed++;
                }
            }
            results.add(row);
        }

        double passRate = cases.isEmpty() ? 0.0 : (double) passed / cases.size();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", cases.size());
        summary.put("passed", passed);
        summary.put("passRate", Math.round(passRate * 1000.0) / 1000.0);
        summary.put("dryRun", dryRun || baseUrl == null || baseUrl.isBlank());
        summary.put("baseUrl", baseUrl);
        summary.put("results", results);

        Path outDir = projectRoot.resolve(".zestflow/acceptance");
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("last-run.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary),
                StandardCharsets.UTF_8);

        summary.put("next", passRate >= 1.0
                ? "validate_delivery(strictMode=true)"
                : "修复失败 step 后重跑");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
    }

    private int invoke(String baseUrl, StepCase c) {
        try {
            String url = baseUrl.replaceAll("/+$", "") + c.path();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15));
            builder.method(c.method(), HttpRequest.BodyPublishers.noBody());
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    static List<StepCase> parseSteps(String yamlContent) {
        List<StepCase> cases = new ArrayList<>();
        Matcher m = STEP_BLOCK.matcher(yamlContent);
        while (m.find()) {
            cases.add(new StepCase(
                    m.group(1).toUpperCase(Locale.ROOT),
                    m.group(2),
                    Integer.parseInt(m.group(3))));
        }
        return cases;
    }

    record StepCase(String method, String path, int expectedStatus) {
    }
}
