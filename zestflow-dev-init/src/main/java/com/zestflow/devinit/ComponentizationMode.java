package com.zestflow.devinit;

import java.util.Locale;

/**
 * 元件化程度，由 {@code --componentization} 指定，默认 {@link #FULL}。
 */
public enum ComponentizationMode {
    FULL("full"),
    HYBRID("hybrid");

    private final String cliValue;

    ComponentizationMode(String cliValue) {
        this.cliValue = cliValue;
    }

    public String cliValue() {
        return cliValue;
    }

    public static ComponentizationMode parse(String raw) {
        if (Strings.isBlank(raw)) {
            return FULL;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ComponentizationMode mode : values()) {
            if (mode.cliValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "未知 --componentization: " + raw + "（可选 full|hybrid，默认 full）");
    }

    public String architectureSection() {
        if (this == HYBRID) {
            return "| 层级 | 职责 | 技术 |\n"
                    + "|------|------|------|\n"
                    + "| HTTP 入口 | 鉴权、参数校验、统一响应 | `@RestController`（薄，不写业务） |\n"
                    + "| 简单 CRUD | 单表/单聚合根增删改查 | `Service` + `Mapper` |\n"
                    + "| **可编排业务流程** | 多步、可观测、可热替换 | **`@ZestComponent` + `@ZestExecute`** |\n"
                    + "\n"
                    + "**策略（hybrid）**：AI **新生成**的多步业务流程必须元件化；单表 CRUD 可继续 `Service` + `Mapper`。\n";
        }
        return "| 层级 | 职责 | 技术 |\n"
                + "|------|------|------|\n"
                + "| HTTP 入口 | 鉴权、参数校验、统一响应 | `@RestController`（薄，不写业务） |\n"
                + "| **业务逻辑（默认）** | 可编排、可观测、可热替换 | **`@ZestComponent` + `@ZestExecute`** |\n"
                + "\n"
                + "**策略（full，默认）**：AI **新生成**的业务逻辑一律以 ZestFlow 元件实现；"
                + "禁止新建 `*Service` 承载业务（持久化/外部调用可封装为元件内部私有类或独立适配元件）。\n"
                + "既有遗留 `Service` 可不主动重构，除非用户明确要求。\n";
    }
}
