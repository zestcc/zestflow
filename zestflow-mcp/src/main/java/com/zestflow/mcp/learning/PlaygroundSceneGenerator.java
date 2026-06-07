package com.zestflow.mcp.learning;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按 HTTP Mode 生成 Playground 场景草稿（P2/P3）。
 */
public class PlaygroundSceneGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PlaygroundSceneDraft generate(String feature, String chainCode, int httpMode,
                                         Map<String, Object> sampleParams) throws Exception {
        Map<String, Object> params = sampleParams != null ? sampleParams : defaultParams(feature);
        String bodyJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(
                buildBody(httpMode, chainCode, params));

        return switch (httpMode) {
            case 1 -> new PlaygroundSceneDraft(
                    feature + " /execute",
                    chainCode,
                    1,
                    "POST",
                    "/execute",
                    bodyJson,
                    "重启 demo 后在 Admin Playground 试跑；body 含 chainCode + params");
            case 2 -> new PlaygroundSceneDraft(
                    feature + " REST",
                    chainCode,
                    2,
                    "POST",
                    "/api/zestflow/demo/" + slug(feature),
                    bodyJson,
                    "链 config.http.path 需与 requestPath 一致；chain-route-enabled=true");
            default -> new PlaygroundSceneDraft(
                    feature + " Controller",
                    chainCode,
                    3,
                    "POST",
                    "/api/demo/" + slug(feature),
                    bodyJson,
                    "需 Controller + ChainGateway；Playground 走 Tomcat 业务 API");
        };
    }

    private static Map<String, Object> buildBody(int httpMode, String chainCode, Map<String, Object> params) {
        if (httpMode == 1) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chainCode", chainCode);
            body.put("params", params);
            return body;
        }
        return params;
    }

    private static Map<String, Object> defaultParams(String feature) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (feature != null && (feature.contains("register") || feature.contains("Register") || feature.contains("注册"))) {
            m.put("phone", "13800138000");
            m.put("password", "Passw0rd!");
            return m;
        }
        m.put("userId", "U-DEMO-001");
        m.put("productId", "SKU-DEMO");
        m.put("quantity", 1);
        return m;
    }

    private static String slug(String feature) {
        if (feature == null) {
            return "feature";
        }
        return feature.replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase();
    }
}
