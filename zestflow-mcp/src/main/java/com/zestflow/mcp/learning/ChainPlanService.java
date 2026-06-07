package com.zestflow.mcp.learning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Chain-first 业务链规划 — 规则模板 + 元件白名单对比（plan_chain Tool 核心）。
 */
public class ChainPlanService {

    private final PatternSearcher patternSearcher;

    public ChainPlanService(PatternSearcher patternSearcher) {
        this.patternSearcher = patternSearcher;
    }

    public ChainPlan plan(String userDescription, String appCode, Set<String> registeredIds) {
        String normalized = userDescription == null ? "" : userDescription.toLowerCase(Locale.ROOT);
        FeatureTemplate template = resolveTemplate(normalized);
        List<PlanStep> steps = new ArrayList<>();
        List<ComponentGap> gaps = new ArrayList<>();

        for (StepTemplate st : template.steps()) {
            String reuse = registeredIds.contains(st.componentId()) ? "reuse" : "new";
            steps.add(new PlanStep(
                    st.stepId(),
                    st.componentId(),
                    st.componentType(),
                    st.nodeType(),
                    st.action(),
                    st.description(),
                    st.reads(),
                    st.writes(),
                    reuse));
            if ("new".equals(reuse)) {
                gaps.add(new ComponentGap(st.componentId(), st.componentType(), "scaffold",
                        "白名单中不存在，需 scaffold_component + IDE Apply"));
            }
        }

        List<String> patterns = patternSearcher.search(userDescription, 3).stream()
                .map(PatternDocument::id)
                .toList();

        return new ChainPlan(
                template.feature(),
                template.featureLabel(),
                template.suggestedChainCode(),
                steps,
                gaps,
                patterns,
                "请选择 HTTP 暴露方式：Mode1=POST /api/execute（无 Controller）；"
                        + "Mode2=链 config.http.path（无 Controller）；"
                        + "Mode3=Controller + ChainGateway。",
                buildWorkflowNext(gaps),
                gaps.isEmpty() ? 0.98 : Math.max(0.85, 0.97 - gaps.size() * 0.02));
    }

    private static String buildWorkflowNext(List<ComponentGap> gaps) {
        if (gaps.isEmpty()) {
            return "compose_chain → validate_chain → bind_http → gen_playground_scene → record_learning_event";
        }
        return "对每个 gap 执行 scaffold_component → compose_chain → validate_chain → bind_http → verify";
    }

    private FeatureTemplate resolveTemplate(String text) {
        if (text.contains("注册") || text.contains("register") || text.contains("signup")) {
            return FeatureTemplate.userRegister();
        }
        if (text.contains("下单") || text.contains("订单") || text.contains("order")) {
            return FeatureTemplate.orderCreate();
        }
        return FeatureTemplate.generic(text);
    }

    private record StepTemplate(
            String stepId,
            String componentId,
            String componentType,
            String nodeType,
            String action,
            String description,
            List<String> reads,
            List<String> writes
    ) {
    }

    private record FeatureTemplate(
            String feature,
            String featureLabel,
            String suggestedChainCode,
            List<StepTemplate> steps
    ) {
        static FeatureTemplate userRegister() {
            return new FeatureTemplate(
                    "userRegister",
                    "用户注册",
                    "CHN_USER_REGISTER",
                    List.of(
                            step("parse", "parseRegisterRequest", "LOADER", "NORMAL",
                                    "解析 HTTP/Playground 入参",
                                    List.of("phone", "password"), List.of("registerReq")),
                            step("validate", "validateRegisterParams", "PARAM_VALIDATOR", "NORMAL",
                                    "参数格式校验",
                                    List.of("phone", "password"), List.of()),
                            step("check", "checkUserExists", "PREDICATE", "CONDITION",
                                    "是否已注册",
                                    List.of("phone"), List.of("userExists")),
                            step("create", "createUser", "EXECUTOR", "NORMAL",
                                    "创建用户记录",
                                    List.of("registerReq"), List.of("userId")),
                            step("notify", "sendNotify", "EXECUTOR", "NORMAL",
                                    "发送注册通知（优先复用 sendNotify）",
                                    List.of("userId", "phone"), List.of()),
                            step("response", "parseRegisterResponse", "PARSER", "NORMAL",
                                    "Mode1/2 HTTP 终态响应",
                                    List.of("userId"), List.of())
                    ));
        }

        static FeatureTemplate orderCreate() {
            return new FeatureTemplate(
                    "orderCreate",
                    "订单创建",
                    "CHN_ORDER_CREATE",
                    List.of(
                            step("validate", "validateUser", "EXECUTOR", "NORMAL",
                                    "校验用户",
                                    List.of("userId"), List.of()),
                            step("create", "createOrder", "EXECUTOR", "NORMAL",
                                    "创建订单",
                                    List.of("userId", "productId", "quantity"), List.of("orderId")),
                            step("notify", "sendNotify", "EXECUTOR", "NORMAL",
                                    "通知",
                                    List.of("orderId"), List.of()),
                            step("response", "parseOrderCreateResponse", "PARSER", "NORMAL",
                                    "HTTP 响应",
                                    List.of("orderId"), List.of())
                    ));
        }

        static FeatureTemplate generic(String text) {
            String slug = text.replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-");
            if (slug.length() > 32) {
                slug = slug.substring(0, 32);
            }
            return new FeatureTemplate(
                    "customFeature",
                    "自定义业务链",
                    "CHN_CUSTOM",
                    List.of(
                            step("load", "loadRequest", "LOADER", "NORMAL",
                                    "解析入参", List.of(), List.of("request")),
                            step("exec", "processBusiness", "EXECUTOR", "NORMAL",
                                    "核心业务", List.of("request"), List.of("result")),
                            step("parse", "parseResponse", "PARSER", "NORMAL",
                                    "HTTP 响应", List.of("result"), List.of())
                    ));
        }

        private static StepTemplate step(String stepId, String componentId, String componentType,
                                         String nodeType, String desc,
                                         List<String> reads, List<String> writes) {
            return new StepTemplate(stepId, componentId, componentType, nodeType,
                    componentType.toLowerCase(Locale.ROOT), desc, reads, writes);
        }
    }
}
