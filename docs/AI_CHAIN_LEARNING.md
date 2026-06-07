# Chain-first AI 学习与沉淀（P1～P3）

> **目标**：意图驱动 + 越用越准（Learning RAG）+ 平台/项目分层 + 晋升门槛 **≥97%**  
> **对标**：LangSmith Feedback、Mem0 策展、Cursor Rules 分层、Stripe MCP 规范绑定

---

## 1. 知识分层

| 层级 | 存储 | 共享 |
|------|------|------|
| **L0 平台** | MCP JAR `zestflow/patterns/platform/` | 全员只读，随版本发布 |
| **L1 团队** | Admin RAG 文档（晋升 import） | 租户 export/import |
| **L2 项目** | `.zestflow/patterns/` + `rules/project.md` | Git 继承 |
| **L3 原始信号** | `.zestflow/learning/events.jsonl` + `zf_ai_learning_event` | 不直接喂 LLM |

---

## 2. 意图工作流

```text
plan_chain → scaffold_component(gap) → compose/validate → bind_http(Mode1/2/3)
  → gen_playground_scene → record_learning_event → distill_patterns → share_pattern
```

| 用户关键字 | MCP Tool |
|------------|----------|
| 开发/规划…链路 | `plan_chain` |
| 生成元件 | `scaffold_component` |
| 组链/验证 | `validate_chain` |
| Mode1/2/3 | 读 Pattern `platform-http-three-mode` |
| 生成场景 | `gen_playground_scene` |
| 反馈/采纳 | `record_learning_event` |
| 蒸馏沉淀 | `distill_patterns` |
| 团队共享 | `share_pattern` → Admin RAG import |

---

## 3. P1 — 学习事件

**Dev（MCP）**：`record_learning_event` → `.zestflow/learning/events.jsonl`

**Admin（Orchestration）**：

- `POST /api/zestflow/ai/learning/events`
- `POST /api/zestflow/ai/sessions/{id}/feedback`（扩展 intent/feature/validate 字段）

---

## 4. P2 — 蒸馏与检索

- `distill_patterns`：高置信事件 → `.zestflow/patterns/*.md` + `index.json`
- `search_patterns` / `plan_chain` 自动检索 **平台 + 项目** Pattern
- **晋升条件（AccuracyGate ≥0.97）**：
  - `validatePassed=true`
  - `validateRounds ≤ 2`
  - `adopted` 或 `playgroundSuccess`
  - 含 intent + feature

---

## 5. P3 — 继承与共享

| 方式 | 操作 |
|------|------|
| Git | 提交 `.zestflow/patterns/`、`rules/project.md` |
| MCP | `share_pattern` → JSON → Admin `POST /ai/rag/documents/import` |
| Admin | `POST /ai/learning/events/{id}/promote-rag`（租户管理员） |

---

## 6. 97% 准确率说明

**不是** LLM 自评 97%，而是：

1. **结构化 plan**（元件类型、I/O、gap 对比）降低幻觉  
2. **Validator 硬门禁** — 未通过不算完成  
3. **晋升策展** — 只有高置信样本进入 Pattern/RAG  
4. **人机确认** — plan / Mode / 发布仍人工  

实测指标：晋升样本在相同 feature 模板上的 **validate 一次通过率** 应 ≥97%（通过 E2E + 晋升样本统计验证）。

---

## 7. 快速开始

```powershell
powershell -File scripts/dev/setup-demo-mcp.ps1
# Cursor 打开 zestflow-demo，对话：
# 「帮我开发注册链路」→ Agent 应调用 plan_chain
# 完成后 record_learning_event → distill_patterns
```

---

*详见 [AI_COPILOT_ACCEPTANCE.md](./AI_COPILOT_ACCEPTANCE.md) 验收用例。*
