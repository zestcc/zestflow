# ZestFlow AI / Executor 知识库 — 跨机续跑交接摘要

> 适用：在另一台机器拉代码后继续开发或验证。  
> 远端：`https://gitee.com/zestcc/zestflow.git`，分支 **`master`**。

---

## 一、架构共识（必须遵守）

```
Admin Copilot / MCP（编排 + LLM）
        ↓ RAG search / learning events / suggest 代理
Executor {dataDir}/ai/          ← 链条知识库主归属
        ├── learning/events.jsonl
        └── patterns/*.md       ← 高置信自动蒸馏
```

| 职责 | 归属 |
|------|------|
| 学习事件、蒸馏、patterns 主库 | **应用端 Executor** |
| LLM 生成、质量门禁、画布 UI | **Admin**（通用验收，无业务硬编码） |
| 租户 RAG / DB 学习事件 | Admin **审计 + 可选补充**（`tenant-rag-auto-promote: false`） |

---

## 二、Git 提交（按时间倒序）

| Commit | 说明 |
|--------|------|
| `cf3d67d` | Admin UI：全屏 Playground 试跑反馈、Executor RAG 设置面板、Vite 分包、CI dev-init |
| `5b34f12` | Admin/MCP：代理 Executor status/suggest；MCP 强制 `--executor-url` |
| `48f04cd` | Executor AI：本机鉴权、去重、validate 后蒸馏、suggest、RAG 分词打分 |
| `cac0d84` | 首版下沉：Executor 知识库、dev-init 增量配置、ai-generation-acceptance、dagre 布局等 |

**新机第一件事：**

```bash
git clone https://gitee.com/zestcc/zestflow.git
cd zestflow
git pull origin master
```

---

## 三、新机环境准备

### 依赖

- JDK **17**
- Maven **3.8+**
- Node **20+**（admin-ui 构建）
- 可选：本地 MySQL、Ollama（Admin Copilot）

### 构建与测试（推荐顺序）

```bash
# 1. 安装 dev-templates（dev-init 测试依赖）
mvn install -pl zestflow-dev-templates -DskipTests

# 2. 核心模块测试
cd zestflow-executor && mvn test -Dtest=ExecutorChainAiServiceTest
cd ../zestflow-admin && mvn test -Dtest=AiCopilotServiceTest,AiCopilotControllerTest,AiLearningEventServiceTest
cd ../zestflow-mcp && mvn test -Dtest=AccuracyGateTest
cd .. && mvn test -pl zestflow-dev-init -am

# 3. 前端
cd zestflow-admin-ui && npm install && npm run build
```

### 启动（本地联调）

1. **业务应用 + Executor**（如 demo，端口通常 `20550`）
2. **Admin**（`8080`）
3. MCP 配置需带：
   - `--executor-url http://127.0.0.1:20550`
   - `--project <业务工程根目录>`
   - 若 Executor 配了 token：`--executor-access-token` 与 `zestflow.executor.access-token` 一致

---

## 四、关键 API 一览

### Executor（应用端）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ai/rag/status` | 知识库状态（事件数、pattern 数） |
| GET | `/api/ai/rag/search?q=&limit=` | RAG 检索 |
| POST | `/api/ai/learning/events` | 学习事件（去重 + 高置信自动蒸馏） |
| POST | `/api/ai/patterns/distill` | 手动蒸馏 |
| POST | `/api/ai/chains/suggest` | 基于 pattern 的链草稿（**非 LLM**） |

**鉴权：**

- 配了 `zestflow.executor.access-token` → 请求头 `X-Access-Token`
- 未配 token → 默认 `ai-localhost-only=true`，仅本机可访问 `/api/ai/*`

### Admin（代理）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ai/rag/status?appCode=` | 租户 RAG + **Executor 主库状态** |
| POST | `/api/ai/executor/chains/suggest` | 代理 Executor suggest |
| POST | `/api/ai/sessions/{id}/feedback` | 采纳/试跑成功 → 转发 Executor |

---

## 五、关键代码路径

| 模块 | 路径 |
|------|------|
| Executor 知识库 | `zestflow-executor/src/main/java/com/zestflow/executor/ai/ExecutorChainAiService.java` |
| 链校验注入 | `zestflow-executor/.../ai/ChainDataValidator.java` + `ExecutorAutoConfig.java` |
| Executor 路由 | `zestflow-executor/.../server/ServerHandler.java` |
| Admin 代理 | `zestflow-admin/.../ai/ExecutorChainAiClient.java` |
| Copilot | `zestflow-admin/.../ai/AiCopilotService.java` |
| MCP 学习 | `zestflow-mcp/.../learning/LearningToolService.java` |
| 前端 Copilot | `zestflow-admin-ui/src/stores/aiCopilot.ts` |
| 全屏 Playground 反馈 | `zestflow-admin-ui/src/views/playground/PlaygroundPage.vue` |
| Executor RAG 面板 | `zestflow-admin-ui/src/components/settings/SettingsAiRagPanel.vue` |
| 画布布局 | `zestflow-admin-ui/src/utils/chainApply.ts`（dagre） |
| 验收规则（三端同源） | `**/ai-generation-acceptance.md` |
| dev-init | `zestflow-dev-init/` + `zestflow-dev-templates/` |

---

## 六、配置要点

**Admin `application.yml`：**

```yaml
zestflow:
  ai:
    tenant-rag-auto-promote: false   # 租户 RAG 不自动晋升，主库在 Executor
  admin:
    executor-access-token: <与 Executor access-token 一致>
```

**Executor：**

```yaml
zestflow:
  executor:
    access-token: <生产建议必配>
    ai-localhost-only: true          # 默认 true
    data-dir: ./zestflow-data        # 知识库在 {dataDir}/ai/
```

**MCP（cursor.mcp.json 等）：**

```json
"args": [
  "-jar", ".../zestflow-mcp.jar",
  "--project", "${workspaceFolder}",
  "--executor-url", "http://127.0.0.1:20550",
  "--executor-access-token", "..."
]
```

> MCP 学习/检索/蒸馏**必须**有 `--executor-url`，已不再走本地 `.zestflow/` 主路径。

---

## 七、学习闭环（端到端）

```
1. Copilot suggest（Admin LLM + Executor RAG）
2. validate-definition（Executor）
3. 采纳 apply → submitFeedback(adopted=true)
   或 试跑成功 → playgroundSuccess=true（内嵌/全屏 Playground）
4. POST Executor /api/ai/learning/events
5. 高置信 → 自动蒸馏 → patterns/*.md
6. 下次 search RAG 复用
```

---

## 八、已完成 vs 待做

### 已完成

- [x] 知识库下沉 Executor
- [x] Admin/MCP 代理 + MCP 强制 executor-url
- [x] 学习事件去重、蒸馏前 validate
- [x] Playground 全链路试跑反馈
- [x] AI 设置页展示 Executor 知识库
- [x] dagre 画布布局、质量门禁、dev-init 增量配置
- [x] CI 增加 `zestflow-dev-init -am`

### 建议下一台机器优先做

1. ~~**Executor 侧 LLM suggest**~~ ✅ v2：OpenAI 兼容 LLM + 质量门禁 + validate 修复 + pattern 回落
2. ~~**Embedding RAG**~~ ✅ v2：keyword / hybrid / TF-IDF + 可选 embedding 重排
3. ~~**复杂 CONDITION 布局**~~ ✅ v2：True 左 / False 右分支偏移（chainApply）
4. **业务工程（如 zestory）** — 跑 `--init-dev`、恢复被改坏的 `application.yml`（在业务仓库）

验收：`docs/acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md`

---

## 九、给另一台机器 AI 的续跑提示词

```
我在 zestflow master 继续 AI/Executor 知识库工作。

架构：链条知识库主路径在 Executor {dataDir}/ai/；Admin 只做 LLM + 代理；
MCP 必须 --executor-url；tenant-rag-auto-promote=false。

请优先实现：Executor 侧 LLM chains/suggest（与 Admin Copilot 能力对齐），
或 Embedding RAG。先读 ExecutorChainAiService、ExecutorChainAiClient、
LearningToolService、ai-generation-acceptance.md。

验证：ExecutorChainAiServiceTest、AiCopilotServiceTest、mvn test -pl zestflow-dev-init -am、npm run build。
```
