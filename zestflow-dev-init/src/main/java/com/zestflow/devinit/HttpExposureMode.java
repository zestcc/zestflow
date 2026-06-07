package com.zestflow.devinit;

/**
 * HTTP 暴露模式（init-dev {@code --http-mode}），对齐平台三 Mode。
 */
public enum HttpExposureMode {
    MODE1(1, "execute"),
    MODE2(2, "chain-route"),
    MODE3(3, "controller");

    private final int mode;
    private final String cliAlias;

    HttpExposureMode(int mode, String cliAlias) {
        this.mode = mode;
        this.cliAlias = cliAlias;
    }

    public int mode() {
        return mode;
    }

    public String cliValue() {
        return String.valueOf(mode);
    }

    public static HttpExposureMode parse(String raw) {
        if (Strings.isBlank(raw)) {
            return MODE3;
        }
        String normalized = raw.trim().toLowerCase();
        if ("1".equals(normalized) || "mode1".equals(normalized) || "execute".equals(normalized)) {
            return MODE1;
        }
        if ("2".equals(normalized) || "mode2".equals(normalized) || "chain-route".equals(normalized)
                || "chainroute".equals(normalized)) {
            return MODE2;
        }
        if ("3".equals(normalized) || "mode3".equals(normalized) || "controller".equals(normalized)) {
            return MODE3;
        }
        throw new IllegalArgumentException(
                "未知 --http-mode: " + raw + "（可选 1|2|3 或 execute|chain-route|controller，默认 3）");
    }

    public String architectureSection() {
        switch (this) {
            case MODE1:
                return "### HTTP 暴露（Mode 1，本项目默认策略）\n\n"
                        + "| 项 | 约定 |\n|----|------|\n"
                        + "| 入口 | `POST /api/execute`，body：`{ chainCode, params }` |\n"
                        + "| Controller | **不生成**业务 Controller 调链 |\n"
                        + "| 响应 | 链末 **PARSER** 节点返回值 |\n"
                        + "| 门禁 | `validate_chain` 通过；链必须含 PARSER 终节点 |\n";
            case MODE2:
                return "### HTTP 暴露（Mode 2，本项目默认策略）\n\n"
                        + "| 项 | 约定 |\n|----|------|\n"
                        + "| 入口 | 链 `config.http.path` + method（需 `chain-route-enabled=true`） |\n"
                        + "| Controller | **不生成** |\n"
                        + "| 响应 | 链末 **PARSER** |\n"
                        + "| 门禁 | 同 Mode1；`bind_http` 写入 http.path |\n";
            default:
                return "### HTTP 暴露（Mode 3，本项目默认策略）\n\n"
                        + "| 项 | 约定 |\n|----|------|\n"
                        + "| 入口 | 薄 `@RestController` + `ChainGateway` |\n"
                        + "| 绑链 | 方法上 **`@ZestChain(\"stable.chain.key\")`** 声明 chain_key；**禁止**在 Controller 写业务 |\n"
                        + "| 请求 | `@RequestBody` 使用 `*Request` DTO；内部转 `Map` 或 Command 调 `executeByKey` |\n"
                        + "| 响应 | 统一 `Result<*Response>`；链失败抛异常便于事务回滚 |\n"
                        + "| 新功能 | AI **必须**生成 Controller 草稿（含 `@ZestChain`），与链/设计图一并产出 |\n";
        }
    }
}
