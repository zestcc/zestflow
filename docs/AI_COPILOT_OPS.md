# ZestFlow Copilot 运维指南

> **版本** 1.1 · **更新** 2026-06-06 · 配合 [AI_COPILOT.md](./AI_COPILOT.md) P5 使用

本文面向平台运维与租户管理员，说明 AI 预设维护、租户 RAG 知识库、用量看板与常见部署配置。

---

## 1. 全局开关与配置来源

### 1.1 运行时优先级

```
有效值 = sys_config（租户 1，Admin UI 可改，热更新）> application.yml 冷启动兜底
```

| 层 | 存储 | 说明 |
|----|------|------|
| 部署/密钥 | yaml + 环境变量 | JWT、registry-token、SMTP、`env-keys` 等 |
| 枚举/级联 | 字典 `ai_provider` / `ai_model` | 提供商与模型列表 |
| 平台可调参数 | `sys_config`（租户 1） | AI 开关、RAG 阈值、Playground 超时等 |
| 租户 AI 运行时 | `zf_ai_tenant_config` | 每租户 preset、Key、配额 |

**运维入口：** 设置 → **系统配置**（平台参数，租户 1 维护）；设置 → **AI 配置**（租户级 preset/Key/RAG 文档）。

### 1.2 application.yml（Admin，仅兜底）

```yaml
zestflow:
  ai:
    enabled: true                    # 全局 Copilot 开关（sys_config: ai.enabled 优先）
    default-preset: deepseek
    timeout-ms: 60000
    max-tokens: 4096
    tenant-auto-init: true           # 非生产可自动写入默认租户 AI 配置
    env-keys:                        # presetId → 环境变量名（兜底 Key，仅 yaml）
      deepseek: DEEPSEEK_API_KEY
      siliconflow: SILICONFLOW_API_KEY
      groq: GROQ_API_KEY
    rag-enabled: true
    rag-mode: hybrid                 # keyword | vector | hybrid
    rag-max-chunks: 3
    rag-tenant-data-dir: ./data/ai-rag
    rag-tenant-filesystem-enabled: true
    rag-tenant-max-documents: 200
    rag-tenant-max-content-bytes: 524288
```

首次启动时 `SystemConfigSeeder` 会将缺失的 `sys_config` 键补齐为 yaml 当前值；之后以 UI 修改为准。

### 1.3 常用环境变量

| 变量 | 用途 |
|------|------|
| `DEEPSEEK_API_KEY` | DeepSeek 预设兜底 Key |
| `SILICONFLOW_API_KEY` | SiliconFlow 预设兜底 Key |
| `GROQ_API_KEY` | Groq 预设兜底 Key |
| `OPENAI_API_KEY` | OpenAI / 兼容网关 |
| `ZESTFLOW_AI_ENABLED` | 覆盖全局 Copilot 开关（若部署脚本支持） |

**原则：** 生产环境 Key 由租户在「设置 → AI 配置」自行保存；平台不托管共享 Key。`tenant-auto-init` 建议生产关闭。

---

## 2. AI 提供商预设维护

提供商枚举由字典 `ai_provider` / `ai_model` 管理（树状级联）。`ai-providers.yaml` **仅在空库首次启动时** 作为种子写入字典，之后通过 **设置 → 字典管理** 维护。

### 2.1 新增或调整预设

1. 在字典 `ai_provider` 下新增条目（或通过 yaml 种子后于 UI 编辑）。
2. 在对应 provider 的 `ai_model` 子节点维护模型列表。
3. 本地 `custom` 预设允许租户填写任意 OpenAI 兼容 Base URL。
4. 标记 `deprecated: true` 时可指定 `successor` 引导迁移。
5. 修改后立即生效；无需重启 Admin。

### 2.2 验证

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/zestflow/ai/providers
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"preset":"ollama","model":"qwen2.5:7b","apiKey":"ollama"}' \
  http://localhost:8080/api/zestflow/ai/test
```

---

## 3. 租户 RAG 知识库（P5-A）

### 3.1 数据来源

| 来源 | 说明 |
|------|------|
| **平台 classpath** | `zestflow-admin/src/main/resources/ai-rag/*.md`，所有租户共享 |
| **租户 DB** | 表 `zf_ai_rag_document`，通过 Admin UI 或 API 维护 |
| **租户文件目录** | `{rag-tenant-data-dir}/{tenantId}/*.md`，可选 |

检索时 **platform + tenant** 合并，按 `hybrid`（TF-IDF + 可选 LLM Embedding）排序，注入 Prompt 的片段数由 `rag-max-chunks` 控制。

### 3.2 Admin UI 操作

路径：**设置 → AI 配置 → 知识库**

- 新建/编辑 Markdown 文档，可选绑定 `appCode`（空表示租户全局）。
- 禁用文档不参与索引。
- **重建索引**：文档变更后点击，或重启 Admin（启动时会加载 classpath + 租户索引）。

### 3.3 文件目录挂载（可选）

```text
./data/ai-rag/
  1/                    # tenantId = 1
    order-rules.md
    aviator-cheatsheet.md
  2/
    ...
```

要求：

- 仅 `.md` 文件；建议用 `##` 分节便于分块。
- 单文件不超过 `rag-tenant-max-content-bytes`（默认 512KB）。
- 每租户 DB 文档数不超过 `rag-tenant-max-documents`（默认 200）。
- 修改文件后执行 **重建索引** 或重启。

Docker 部署时将 `./data/ai-rag` 挂载为持久卷。

### 3.4 API 速查

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/ai/rag/status` | 登录用户 |
| GET | `/api/ai/rag/search?q=&appCode=&limit=` | 登录用户 |
| GET | `/api/ai/rag/documents` | 登录用户 |
| POST/PUT/DELETE | `/api/ai/rag/documents` | 租户管理员 |
| POST | `/api/ai/rag/documents/rebuild-index` | 租户管理员 |

---

## 4. 用量与审计看板（P5-B）

路径：**设置 → AI 配置 → 用量统计**

指标来自 `zf_ai_copilot_session` / `zf_ai_copilot_message`：

| 指标 | 说明 |
|------|------|
| 会话总数 / 成功率 | 基于 `success` 字段 |
| 平均延迟 | `latency_ms` 均值（LLM 调用；规则脚手架可为 0） |
| Token 估算 | message 表 `token_estimate` 合计 |
| 采纳率 | `adopted=1` / 有 feedback 的会话 |
| 按 mode / 日趋势 | explain、suggest、expression、diagnose 等 |

API：`GET /api/ai/usage/overview?days=7|30|90`（租户管理员）

**隐私：** 消息表仅存 `content_summary` 摘要，不存完整 Prompt。

---

## 5. 数据库迁移

Flyway `V1__init_admin_schema.sql`（Beta 整合，含原 AI V3–V5）：

- `zf_ai_rag_document`、`zf_ai_tenant_config`、`zf_ai_copilot_session` 等 AI 表
- `zf_ai_tenant_config` 含 `monthly_token_quota`（月 Token 上限，NULL=不限）
- `zf_ai_copilot_session` 含 `latency_ms`、`success`、`error_message`

新环境启动 Admin 后 Flyway 自动执行 V1；已有 beta 库建议删库重建。

---

## 6. 故障排查

| 现象 | 排查 |
|------|------|
| Copilot 灰显 | 检查 `sys_config` `ai.enabled`（或 yaml 兜底）、租户「启用 Copilot」、Key 是否配置 |
| RAG 无命中 | `/api/ai/rag/status` 看 `platformChunks` / `tenantChunks`；确认文档 `enabled=1` |
| 文件目录不生效 | `rag-tenant-filesystem-enabled=true`；路径 `{dir}/{tenantId}/*.md`；重建索引 |
| 用量为空 | 需有 Copilot 会话；仅 scaffold 规则模板时部分指标为 0 |
| 测试连接失败 | 核对 preset、`baseUrl`、网络出网、Ollama 是否监听 |

E2E 脚本：`scripts/blackbox/run-ai-copilot-e2e.ps1`（含 RAG、用量 API 探测）。

---

## 7. 安全提醒

- Copilot **不会** 自动 save / publish / reload。
- LLM **仅** Admin 调用；前端不直连第三方。
- 租户 RAG 含业务知识时，按租户隔离；勿将敏感 Key 写入 Markdown。
- 生产关闭 `tenant-auto-init`，避免误用环境变量 Key。
