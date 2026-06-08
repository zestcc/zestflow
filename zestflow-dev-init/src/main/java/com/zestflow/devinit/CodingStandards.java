package com.zestflow.devinit;

/**
 * 通用编码约束（写入 architecture.md，IDE/MCP 同源）。
 */
public final class CodingStandards {

    private CodingStandards() {
    }

    public static String architectureSection() {
        return "## 通用编码约束（AI / MCP 必须遵守）\n\n"
                + "### 持久化防腐（Repo 层）\n\n"
                + "- 元件 / Handler **禁止**直接注入 `*Mapper`。\n"
                + "- 中间必须有 **`*Repo` / `*Repository` 接口 + 实现**，封装 MyBatis/JPA 细节。\n"
                + "- 分层：`@ZestExecute` → `*Repo` → `*Mapper`；领域对象用 **DO**，对外用 **VO**。\n\n"
                + "### AI 生成唯一规则（`ai-generation-acceptance`）\n\n"
                + "- 所有 AI 生成须站在**验收标准**：对标市面成熟方案 + 检索内部 RAG，**90%** happy path 可跑。\n"
                + "- 高置信结果须 **record → 自动蒸馏 RAG**；禁止单节点黑盒与一次性 Prompt 敷衍。\n\n"
                + "### 新功能交付（链 + 设计图）\n\n"
                + "完整新功能须一次性交付，不得只写孤立元件：\n\n"
                + "1. `plan_chain` → 元件清单与 gap\n"
                + "2. `scaffold_component` → 元件 + Repo + DTO/VO（IDE Apply）\n"
                + "3. **`compose_chain` / 链 JSON** → `validate_chain`\n"
                + "4. **`gen_smoke_suite` → `run_acceptance_suite` → `validate_delivery(passed=true)`**（未完成禁止宣称交付完成）\n"
                + "5. **设计图 graph_data** 与链绑定（Admin 设计器可导入/同步）\n"
                + "6. 按 HTTP Mode 绑定入口（Mode3 含 `@ZestChain` Controller）\n"
                + "7. `gen_playground_scene` 验证\n\n"
                + "### 交付门禁（Delivery Gate · 强制）\n\n"
                + "- bootstrap 占位链（Seeder）**不等于** production 交付；功能须 `compose_chain` + `lifecycle=production`。\n"
                + "- `usable_score ≥ 0.95` 且 `blocking=0` 方可向用户声明完成（MCP `validate_delivery`）。\n"
                + "- 禁止单体 `@ZestExecute` >80 行承载完整用例；须按 Pattern 拆节点。\n\n"
                + "MCP 可生成链与设计草稿；**发布/写库由人在 Admin 确认**。\n\n"
                + "### 元件注册与 JavaDoc\n\n"
                + "- 每个 `@ZestExecute`（及 PARSER/PREDICATE 等）**必须有完整 JavaDoc**。\n"
                + "- 启动扫描时：**注解 `description` 优先**；为空则从 **JavaDoc 灌入 Admin 备注**（含 `@param`/`@return`）。\n"
                + "- Admin 元件列表、MCP `list_components` 均展示该备注，供 AI 建链。\n\n"
                + "### 自动生成代码的注释标准\n\n"
                + "方法 JavaDoc **至少包含**：\n\n"
                + "- 业务说明（一句话 + 边界/副作用）\n"
                + "- `@param` 每个参数：含义、类型语义、**必填/可选**、校验规则\n"
                + "- `@return` 返回值含义；无返回写 `@return void`\n"
                + "- 若有 `@ZestParam` / `@ZestOutput`，与 JavaDoc 保持一致\n\n"
                + "### 入参对象化（PO / DO / DTO / VO）\n\n"
                + "| 场景 | 类型命名 | 说明 |\n"
                + "|------|----------|------|\n"
                + "| 持久化实体 | `*DO` | 与表字段对应，仅在 Repo/Mapper 层 |\n"
                + "| 写库/内部传递 | `*PO` 或 Command | 创建/更新命令 |\n"
                + "| 跨层/元件入参 | `*DTO` / `*Command` / `*Query` | 超过 **2 个**平铺参数时必须用对象 |\n"
                + "| 校验复杂 | `*Request` | 多个校验注解或组合条件 |\n"
                + "| HTTP/对外响应 | `*VO` / `*Response` | Controller 返回体 |\n\n"
                + "**硬性规则**：`@ZestExecute` 方法平铺参数 **≤ 2**；否则改为 **一个** `*Command`/`*Query` DTO + `@ZestParam` 绑定。\n\n"
                + "### 配置安全（严禁破坏已有工程）\n\n"
                + "- **禁止** AI/MCP/init-dev **覆盖或删除** 已有：`application.yml`、`application-local.yml`、"
                + "`application-prod.yml`、各模块 `pom.xml`（即使 `--force` 也不生效）。\n"
                + "- **禁止**擅自把数据源改成 **H2**；缺数据源时仅 seed `application-local.example.yml`（MySQL `root`/`root`），"
                + "**不写** `application-local.yml`。\n"
                + "- init-dev **按缺口增量补齐**：缺 `application.yml` 则新建；缺 `zestflow.*` 则新建 `application-zestflow.yml`，"
                + "并在已有 `application.yml` 末尾用 `---` **仅追加** import（不整文件替换）。\n"
                + "- 缺 `zestflow-starter` 依赖时生成 `.zestflow/bootstrap/zestflow-starter-dependency.snippet.xml`，由人合并进 pom。\n"
                + "- 已有 H2/MySQL/端口等配置**一律保留**；只补 ZestFlow 缺失项。\n";
    }
}
