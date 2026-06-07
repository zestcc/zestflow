# ZestFlow AI 全流程生产级验收规范

| 项目 | 内容 |
|------|------|
| 版本 | 1.0 |
| 更新 | 2026-06-02 |
| 范围 | **Orchestration Copilot**（Admin）+ **Dev Copilot**（`zestflow-mcp`） |
| 关联设计 | [AI_COPILOT.md](./AI_COPILOT.md) · [AI_DEV_COPILOT_FINAL_SOLUTION.md](./AI_DEV_COPILOT_FINAL_SOLUTION.md) · [MCP_SETUP.md](./MCP_SETUP.md) |
| 自动化入口 | `scripts/blackbox/run-ai-copilot-acceptance.ps1` |

---

## 1. 验收模型

```text
Layer U  单元测试（Mock LLM / 无网络）     → mvn test
Layer I  集成测试（Controller / MCP 模块）  → mvn test
Layer B  API 黑盒（Admin + Executor）       → run-ai-copilot-e2e.ps1
Layer M  Dev MCP 黑盒（CLI + Executor）     → run-ai-mcp-e2e.ps1
Layer P  性能 / 压测（无 LLM + 可选 Mock）  → run-ai-copilot-perf.ps1
Layer M  人工 UI 走查（设计器 / 设置页）    → 本文 §6 手工用例
```

**发布门禁建议**

| 环境 | 必跑 |
|------|------|
| CI（无 LLM） | Layer U + I；`-AllowLlmSkip` 黑盒 |
| 预发 | 全 Layer + `-RequireLlm -UseMockLlm` |
| 生产前 | 全 Layer + 真实 Ollama/DeepSeek 抽样 3 条 LLM 用例 |

**元件 AI 说明**：Admin 元件页「AI 脚手架」**已移除**；元件创建能力验收归属 **Dev Copilot**（MCP `scaffold_component` + IDE Apply），见 §4。

---

## 2. 环境与前置

| 项 | 要求 |
|----|------|
| JDK | 17+，`JAVA_HOME` 指向含 `bin\java.exe` 的根目录 |
| Admin | `:8080`，`admin/admin123` |
| Demo Executor | `:20550`，`app-code=demo-app` |
| MySQL | 已执行 `scripts/init.ps1` + `initData.ps1` |
| MCP JAR | `powershell -File scripts/dev/install-mcp.ps1`（Layer M 必跑，装到 `~/.zestflow/tools/`） |
| LLM（可选） | Ollama `qwen2.5:7b` 或 `-UseMockLlm` |

---

## 3. 自动化脚本对照

| 脚本 | 覆盖 Layer | 说明 |
|------|------------|------|
| `mvn test -pl zestflow-admin -Dtest=Ai*Test` | U/I | Admin AI 单测 |
| `mvn -Pdev-mcp test -pl zestflow-mcp` | U/I | MCP 单测 |
| `run-ai-copilot-e2e.ps1` | B | Admin Copilot API 全量 |
| `run-ai-mcp-e2e.ps1` | M | MCP CLI + Executor 对齐 |
| `run-ai-copilot-perf.ps1` | P | validate/RAG 并发压测 |
| `run-ai-copilot-acceptance.ps1` | 汇总 | 上述一键门禁 |

报告输出：`scripts/blackbox/results/ai-*.json`（本地，不入库）。

---

## 4. Dev Copilot — 元件 AI 创建（MCP）

> **生产路径**：Cursor 打开 `zestflow-demo` → MCP Tools → IDE Apply 落盘。  
> **禁止**：Admin `POST /ai/component/scaffold`（已删除）。

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-MCP-001 | P0 | U | 脚手架生成 | `ComponentScaffoldGenerator.scaffold(...)` | 含 `@ZestExecute`、`suggestedRelativePath`、Apply 提示 | `ComponentScaffoldGeneratorTest` |
| TC-MCP-002 | P0 | U | 源码搜索 | `ProjectSourceSearcher.search` 关键词 `@ZestComponent` | 返回 JSON 命中列表 | `ProjectSourceSearcherTest` |
| TC-MCP-003 | P0 | U | 规范资源加载 | `ResourceLoader` 读取 classpath MD | 非空、UTF-8 | `ResourceLoaderTest` |
| TC-MCP-004 | P0 | U | CLI 参数解析 | `--export-task-package -o file.md` | 不启动 stdio MCP | `McpRuntimeConfigParserTest` |
| TC-MCP-005 | P0 | B | 任务包 CLI 导出 | `java -jar ... --export-task-package --project zestflow-demo ...` | Markdown 含规范摘要、元件白名单、`project.md` | `run-ai-mcp-e2e.ps1` → `mcp-cli-export` |
| TC-MCP-006 | P0 | B | 元件白名单 | MCP 等价 HTTP `GET :20550/api/components` | 200，含 `validateUser` 等 demo 元件 | `mcp-executor-list-components` |
| TC-MCP-007 | P0 | B | 链校验（合法） | 提交含 `validateUser` 的 ChainDefinition | `valid=true` | `mcp-executor-validate-valid` |
| TC-MCP-008 | P0 | B | 链校验（非法 componentId） | 提交 `component=__NOT_REGISTERED__` | `valid=false`，含错误信息 | `mcp-executor-validate-invalid` |
| TC-MCP-009 | P1 | B | demo MCP 配置 | 检查 `zestflow-demo/.cursor/mcp.json` | `${userHome}/.zestflow/tools/` + `workspaceFolder`，`app-code=demo-app` | `mcp-demo-cursor-config` |
| TC-MCP-010 | P1 | B | 生产打包隔离 | 默认 `mvn package` reactor | **不含** `zestflow-mcp` 模块 | 人工 / CI pom 审查 |
| TC-MCP-011 | P1 | B | JAR 不进 demo 包 | `package-demo.ps1` 产物 | Spring Boot 内无 MCP JAR | 人工解压验收 |
| TC-MCP-012 | P2 | M | Cursor 端到端 | Agent 调用 `list_components` → `scaffold_component` → Apply | Java 文件出现在约定包路径；`mvn compile` 通过 | 手工 |
| TC-MCP-013 | P2 | M | 审计日志 | 调用任意 Tool 后 | `.zestflow/mcp-audit.jsonl` 追加一行 | 手工 |
| TC-MCP-014 | P2 | B | 路径穿越防护 | `read_project_file` 传 `../../etc/passwd` | 拒绝 / 错误，不泄露 | 手工 + 单测扩展 |

### 4.1 Chain-first 学习与沉淀（P1～P3）

> 设计详见 [AI_CHAIN_LEARNING.md](./AI_CHAIN_LEARNING.md)。平台 Pattern（L0）在 MCP JAR；项目 Pattern（L2）在 `.zestflow/patterns/`；租户 RAG（L1）经 Admin 晋升。

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-LRN-001 | P0 | U | 晋升门槛 | `AccuracyGate.evaluate` 高置信事件 | `passed=true`，score≥0.97 | `AccuracyGateTest` |
| TC-LRN-002 | P0 | U | 验证失败拒绝 | validatePassed=false | `passed=false` | `AccuracyGateTest` |
| TC-LRN-003 | P0 | U | 注册链规划 | `ChainPlanService.plan("注册链路")` | feature=userRegister，含 gap/reuse | `ChainPlanServiceTest` |
| TC-LRN-004 | P0 | B | 平台 Pattern 打包 | MCP JAR 内 `zestflow/patterns/platform/` | 含 index.json、http-three-mode | `run-ai-mcp-e2e.ps1` → `mcp-platform-patterns` |
| TC-LRN-005 | P0 | B | 项目 learning 目录 | `zestflow-demo/.zestflow/learning/` | `.gitignore` 忽略 events.jsonl | `run-ai-mcp-e2e.ps1` → `mcp-demo-learning-dir` |
| TC-LRN-006 | P0 | B | Admin 记录事件 | `POST /ai/learning/events` | 200，含 promotionScore | `run-ai-copilot-e2e.ps1` → `ai-learning-events-save` |
| TC-LRN-007 | P0 | B | Admin 列表 | `GET /ai/learning/events?appCode=demo-app` | 含刚写入事件 | `ai-learning-events-list` |
| TC-LRN-008 | P0 | B | 低分不可晋升 | 未达门槛事件 `POST .../promote-rag` | 4xx validation | `ai-learning-promote-rejected` |
| TC-LRN-009 | P0 | B | 高分晋升 RAG | 高置信事件 promote-rag | 返回 RAG document id | `ai-learning-promote-rag` |
| TC-LRN-010 | P1 | M | MCP plan_chain | Cursor Agent「开发注册链路」 | 调用 plan_chain + validate_chain | 手工 |
| TC-LRN-011 | P1 | M | 蒸馏沉淀 | record → distill_patterns | `.zestflow/patterns/*.md` 更新 | 手工 |
| TC-LRN-012 | P1 | M | 团队共享 | share_pattern → RAG import | 租户检索命中 | 手工 |

---

## 5. Orchestration Copilot — Admin AI 建链

### 5.1 平台配置与开关

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-001 | P0 | B | 全局 Copilot 开关 | `GET /system/features` | `copilot.globallyEnabled=true`（试玩环境） | `copilot-globally-enabled` |
| TC-ADM-002 | P0 | B | AI 配置可读 | `GET /ai/config` | 200，含 enabled、预设信息 | `ai-config` |
| TC-ADM-003 | P0 | B | 预设列表 | `GET /ai/providers` | ≥20 条预设 | `ai-providers-list` |
| TC-ADM-004 | P0 | B | 租户 AI 保存 | `PUT /ai/tenant-config` | 200 | `ai-tenant-config-save` |
| TC-ADM-005 | P0 | B | 租户 AI 读取 | `GET /ai/tenant-config` | 含 enabled、preset | `ai-settings-tab-config-api` |
| TC-ADM-006 | P0 | B | 连接测试 | `POST /ai/test`（Mock LLM） | success=true | `ai-test-connection` |
| TC-ADM-007 | P1 | U | Copilot 关闭拦截 | `isCopilotEnabled=false` 时 explain | 抛 `AI_COPILOT_DISABLED` | `AiCopilotServiceTest` |
| TC-ADM-008 | P1 | M | 未配置 Key 灰显 | 租户 enabled 但无 Key | UI Copilot 入口禁用 + 引导设置 | 手工 |

### 5.2 设计器上下文

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-010 | P0 | B | 元件白名单上下文 | `GET /ai/context/components?appCode=demo-app` | JSON 含已注册 componentId | `ai-context-components` |
| TC-ADM-011 | P0 | B | chainCtx 键提示 | `GET /ai/context/chain-keys?appCode=demo-app` | declaredKeys + adminKeys 非空 | `ai-chain-key-hints` |

### 5.3 链校验（无 LLM）

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-020 | P0 | B | 合法链校验 | `POST /ai/design/validate` 含 `validateUser` | valid 字段存在 | `ai-validate-chain` |
| TC-ADM-021 | P0 | B | 非法 componentId | validate 含未注册元件 | `valid=false` | `ai-validate-invalid-component` |
| TC-ADM-022 | P0 | U | repair loop | suggest 返回非法 JSON → 第二轮合法 | `repairRounds=1`，最终 valid | `AiCopilotServiceTest.suggest_shouldRepairUntilValid` |

### 5.4 建链能力（LLM）

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-030 | P0 | B | 解释链 | `POST /ai/design/explain` | `explanation` 非空，≤120s | `ai-design-explain` |
| TC-ADM-031 | P0 | B | 生成链草稿 | `POST /ai/design/suggest` mode=generate | `proposedChainData` + `validation` | `ai-design-suggest` |
| TC-ADM-032 | P1 | B | 修改链草稿 | suggest mode=refine + 现有 chainData | 返回 diff 级 proposal | 手工 / 扩展 e2e |
| TC-ADM-033 | P0 | M | 应用到画布 | UI「应用到画布」 | 画布更新；**未**自动保存/发布 | 手工 |
| TC-ADM-034 | P0 | M | 撤销应用 | UI 撤销 | 画布回滚 | 手工 |
| TC-ADM-035 | P0 | M | 非法提议不可发布 | validation.valid=false 时点发布 | 发布被 Validator 拦截 | 手工 |
| TC-ADM-036 | P1 | U | Markdown 围栏解析 | LLM 返回 ` ```json ` 包裹 | 正确解析 chainData | `parseChainProposal` 单测 |

**建链 SLA（MVP）**：从发送用户消息到得到可校验草稿 ≤ **60 秒**（Mock LLM / 本地 Ollama，P95）。

### 5.5 表达式助手

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-040 | P0 | B | 表达式建议 | `POST /ai/expression/suggest` | `expression` 非空 | `ai-expression-suggest` |
| TC-ADM-041 | P1 | M | 设计器表达式面板 | 选中节点 → Copilot 表达式 Tab | 可插入建议表达式 | 手工 |

### 5.6 日志诊断

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-050 | P0 | B | 诊断 API | `POST /ai/logs/diagnose` | `diagnosis` 非空 | `ai-logs-diagnose` |
| TC-ADM-051 | P0 | U | Trace + LLM | 有 executionId + Collector trace | 非 stub，含节点名 | `AiCopilotServiceTest.diagnose_shouldUseTraceAndLlm` |
| TC-ADM-052 | P1 | M | 跳转设计器 | 诊断结果链接 | 打开对应 design/chain | 手工 |

### 5.7 模板库

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-060 | P0 | B | 列表 | `GET /ai/templates?appCode=demo-app` | 200 | `ai-templates-list` |
| TC-ADM-061 | P0 | B | 保存 | `POST /ai/templates` | 返回 id | `ai-templates-save` |
| TC-ADM-062 | P0 | B | 详情 | `GET /ai/templates/{id}` | 与保存一致 | `ai-templates-get` |
| TC-ADM-063 | P0 | B | 删除 | `DELETE /ai/templates/{id}` | 200 | `ai-templates-delete` |

### 5.8 RAG

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-070 | P0 | B | 平台检索 | `GET /ai/rag/search?q=Aviator` | hits>0 | `ai-rag-search` |
| TC-ADM-071 | P0 | B | 向量模式 | `GET /ai/rag/status` | mode=vector/hybrid* | `ai-rag-vector-mode` |
| TC-ADM-072 | P0 | B | 租户文档 CRUD | POST/PUT/DELETE documents | 成功 | `ai-rag-documents-*` |
| TC-ADM-073 | P0 | B | 重建索引 | POST rebuild-index | 200 | `ai-rag-rebuild-index` |
| TC-ADM-074 | P0 | B | 租户检索 | rebuild 后 search 更新内容 | 命中新文档 | `ai-rag-tenant-search-after-rebuild` |
| TC-ADM-075 | P1 | B | 导入导出 | export + import | imported>0 | `ai-rag-documents-export/import` |
| TC-ADM-076 | P1 | U | RAG 服务 | hybrid 检索排序 | 相关 chunk 在前 | `AiRagServiceTest` |

### 5.9 用量与配额

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-080 | P0 | B | 用量概览 | `GET /ai/usage/overview?days=30` | totalSessions、sessionsByMode | `ai-usage-overview` |
| TC-ADM-081 | P1 | B | 配额字段 | 同上 | monthlyTokenUsed 存在 | `ai-usage-quota-fields` |

### 5.10 安全与多租户

| ID | 优先级 | 类型 | 场景 | 步骤 | 期望 | 自动化 |
|----|--------|------|------|------|------|--------|
| TC-ADM-100 | P0 | B | 无 JWT | 访问 `/ai/design/suggest` | 401/403 | `run-rbac-horizontal-e2e.ps1` |
| TC-ADM-101 | P0 | M | 租户隔离 | multi 模式下租户 A/B | A 的 AI 会话 B 不可见 | `run-tenant-multi-e2e.ps1` + 手工 |
| TC-ADM-102 | P0 | M | Key 不落日志 | 开启 debug 日志跑 explain | 日志无 apiKey 明文 | 人工 grep |
| TC-ADM-103 | P0 | M | AI 不能 reload | Copilot 输出中无 reload 指令；无对应 API 调用 | 无 `PUT /reload` | 手工 + 架构审查 |

---

## 6. 人工 UI 走查清单

| ID | 页面 | 操作 | 期望 |
|----|------|------|------|
| TC-UI-001 | 设计器 | 打开 Copilot Drawer | Explain / Suggest / Validate Tab 可用 |
| TC-UI-002 | 设计器 | Suggest → 预览 diff | 仅 proposal，不自动保存 |
| TC-UI-003 | 设计器 | 应用到画布 | 可撤销；保存仍人工 |
| TC-UI-004 | 设计器 | 表达式 Copilot | 建议可插入节点表达式 |
| TC-UI-005 | 日志页 | AI 诊断 | 打开 Drawer，展示 diagnosis |
| TC-UI-006 | 设置 → AI | 四 Tab（配置/提供商/RAG/用量） | 与 API 数据一致；**无**「开发助手 (MCP)」 |
| TC-UI-007 | 元件页 | 无「AI 脚手架」按钮 | **已移除** |
| TC-UI-008 | Playground | Copilot 不可用或只读提示 | 与产品策略一致 |

---

## 7. 性能与压测

| ID | 指标 | 阈值（试玩环境参考） | 脚本 |
|----|------|----------------------|------|
| TC-PERF-001 | `POST /ai/design/validate` 并发 20×50 | 错误率 0%；P95 < 2s | `run-ai-copilot-perf.ps1` |
| TC-PERF-002 | `GET /ai/rag/search` 并发 10×30 | 错误率 0%；P95 < 3s | 同上 |
| TC-PERF-003 | Mock LLM explain 并发 5×10 | 错误率 0%；P95 < 5s | 同上 `-UseMockLlm` |
| TC-PERF-004 | MCP CLI export 连续 10 次 | 均成功；单次 < 10s | `run-ai-mcp-e2e.ps1` |

---

## 8. 回归命令（发布前复制执行）

```powershell
$env:JAVA_HOME = "D:\IT\JDK17\jdk-17.0.19+10"   # 按本机修改
cd D:\project\2\zestflow

# 1. MCP JAR（Layer M 前置）
powershell -File scripts/dev/install-mcp.ps1

# 2. 启动 Admin:8080 + demo:20550 后：

# 3. 全量 AI 验收（含单测 + 黑盒 + MCP + 压测）
powershell -File scripts/blackbox/run-ai-copilot-acceptance.ps1 -RequireLlm -UseMockLlm

# 4. 仅黑盒（无 LLM 时）
powershell -File scripts/blackbox/run-ai-copilot-e2e.ps1 -AllowLlmSkip
powershell -File scripts/blackbox/run-ai-mcp-e2e.ps1
```

**通过标准**：各脚本 exit 0；`results/ai-*.json` 中 `ok=false` 条目为 0；手工 §6 全勾选。

---

## 9. 变更记录

| 日期 | 变更 |
|------|------|
| 2026-06-02 | 初版：双 Copilot 验收矩阵；Admin scaffold 移除；MCP 元件创建归 Layer M |
| 2026-06-02 | 追加 P1～P3 学习验收 TC-LRN-001～012；12 MCP Tools |

---

*本文档为 AI 功能生产验收唯一用例基线；新增 API 或 Tool 须同步追加 TC 编号并更新黑盒脚本。*
