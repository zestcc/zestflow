# StrictV1 全量验收指南（v1.0.0 发版门禁）

> **类型**：How-to · **目标**：`run-v1-acceptance.ps1` **Exit 0（无 skip）** · [← v1 路线图](../V1_0_ROADMAP.md) · [生产验收](PRODUCTION_ACCEPTANCE.md)

## 1. 验收标准是什么

**StrictV1 全绿** = 以下阶段全部 `ok: true`，且 **不能** 使用 `-SkipProfilesE2e` / `-SkipProductionAcceptance`：

| 阶段 | 内容 |
|------|------|
| `mvn-test-full` | 全仓库 `mvn test` |
| `mvn-test-admin-cluster` | `mvn test -pl zestflow-admin -Pcluster -am` |
| `npm-run-build` | `zestflow-admin-ui` → Admin static |
| `all-profiles-e2e` | local → enterprise → security → playground-disabled → perf |
| `production-acceptance-strictV1` | perf + offline + 主链路 + 压力（`-StrictV1`） |

报告：`scripts/blackbox/results/v1-acceptance-*.json`（`exitCode` 必须为 `0`，且后两阶段 `note` 不能是 `skipped`）。

---

## 2. 环境前提（必读）

### 2.1 端口契约

StrictV1 脚本**固定**使用以下地址（与 `run-all-profiles-e2e.ps1` 一致）：

| 服务 | 端口 | 说明 |
|------|------|------|
| Admin | **8080** | HTTP + 内嵌前端 |
| Demo Tomcat | 8081 | 业务 HTTP（E2E 间接依赖） |
| Executor Netty | **20550** | 链执行 |
| Collector Netty | **20650** | 事件查询 |

**8082 本地调试**：若存在 `application-local.yml` 把 Admin 改到 8082，E2E 会自动叠加 **`strictv1-e2e`** profile 强制回到 **8080**（见 `application-strictv1-e2e.yml`），**无需**手动改回 local 配置。

> production-acceptance 在 `-StrictV1` 且 Admin 不可达时会**自动启栈**（`local,strictv1-e2e`）。

### 2.2 软件与数据

- **JDK 17+**（脚本默认 `D:\IT\JDK17\jdk-17.0.19+10`，可用 `$env:JAVA_HOME` 覆盖）
- **MySQL 8**（或兼容版本），三库已初始化：
  - `zestflow_admin` — Flyway 由 Admin 启动自动迁移
  - `zestflow_app_bussiness` / `zestflow_app_log` — demo `init.sql` + `initData.sql`
- **Node.js 18/20** + npm（`npm run build` 阶段）
- 磁盘空闲 ≥ **5 GB**（全量测试 + 多轮 E2E 启停）

首次启栈前脚本会自动执行 `mvn install -pl zestflow-admin,zestflow-demo -am`（见 `_acceptance-stack.ps1`），确保 `1.0.0-SNAPSHOT` 依赖已装入本地仓库。

### 2.3 发版前释放端口（Windows）

```powershell
foreach ($p in 8080, 8081, 20550, 20650) {
  Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object { if ($_ -and $_ -ne 0) { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue } }
}
Start-Sleep -Seconds 3
```

---

## 3. 推荐执行顺序

### Step 0 — 确认仓库与构建

```powershell
cd D:\project\zestflow
git pull
# 可选：先单独验证白盒（约 3 分钟）
mvn -B test
```

### Step 1 — 启动依赖栈（手动或交给 E2E 脚本）

`run-v1-acceptance.ps1` 会通过 `run-all-profiles-e2e.ps1` **自动**启停 Admin/Demo，无需你长期手动保活。但需确保：

1. MySQL 可连（Admin `application.yml` 或 `application-local.yml` 数据源正确）
2. **8080 未被其他进程占用**
3. 首次 Flyway 冷启动 Admin 可能 **>3 分钟**，E2E 脚本内 `Wait-Admin` 超时 240s，一般足够

### Step 2 — 全量 StrictV1（发版唯一命令）

```powershell
$env:JAVA_HOME = "D:\IT\JDK17\jdk-17.0.19+10"   # 按本机调整
cd D:\project\zestflow

.\scripts\blackbox\run-v1-acceptance.ps1
```

**预计耗时**：30–90 分钟（视机器与 MySQL 性能；含 perf JMH + 多 profile 重启）。

### Step 3 — 检查报告

```powershell
Get-ChildItem scripts/blackbox/results/v1-acceptance-*.json |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1 |
  Get-Content | ConvertFrom-Json |
  Format-List exitCode, strictV1, phases
```

**通过条件**：

- `exitCode -eq 0`
- 五个 phase 的 `ok` 均为 `true`
- `all-profiles-e2e` 与 `production-acceptance-strictV1` 的 `note` **不是** `skipped`

---

## 4. 分阶段调试（失败时）

不必每次跑 90 分钟，可按层定位：

```powershell
# 仅白盒 + cluster + 前端 build（约 5–10 分钟）
.\scripts\blackbox\run-v1-acceptance.ps1 -SkipProfilesE2e -SkipProductionAcceptance

# 仅全 profile E2E（需 8080 栈，约 40–60 分钟）
.\scripts\blackbox\run-all-profiles-e2e.ps1 -SkipMavenTest

# 仅严格生产门禁（需 Admin:8080 + Demo:20550 + Collector:20650）
.\scripts\blackbox\run-production-acceptance.ps1 -SkipMavenTest -StrictV1
```

| 失败阶段 | 优先查看 |
|----------|----------|
| `mvn-test-full` | 控制台 Surefire 失败用例 |
| `mvn-test-admin-cluster` | `zestflow-admin` cluster profile 编译/测试 |
| `npm-run-build` | `zestflow-admin-ui` ESLint/TS 错误 |
| `all-profiles-e2e` | `scripts/blackbox/results/all-profiles-e2e-*.json` 中第一个 `ok: false` |
| `production-acceptance-strictV1` | `production-acceptance-*.json` + [PRODUCTION_ACCEPTANCE.md](PRODUCTION_ACCEPTANCE.md) |

---

## 5. StrictV1 通过后的发版清单（P0 剩余项）

代码 StrictV1 全绿 **≠** 对外已是 `v1.0.0`，还需人工完成：

| # | 动作 | 参考 |
|---|------|------|
| 1 | 版本 `1.0.0-SNAPSHOT` → **`1.0.0`**（父 POM properties + README + CHANGELOG） | [V1_0_ROADMAP.md](../V1_0_ROADMAP.md) |
| 2 | 更新 `ARCHITECTURE.md` §8.5 与 CHANGELOG 定稿日期 | 已含 API 冻结声明 |
| 3 | **`git tag v1.0.0`** + Gitee Release（admin tar.gz/zip） | README 下载链接改为 v1.0.0 |
| 4 | **Maven Central** 发布 `cn.zestflow.www:zestflow-starter:1.0.0` | [PUBLISH_HANDOFF.md](../PUBLISH_HANDOFF.md) |
| 5 | 生产 checklist（密钥、prod profile、SSO secret） | [PRODUCTION_ACCEPTANCE.md](PRODUCTION_ACCEPTANCE.md) §生产 profile |

---

## 6. 常见问题

| 现象 | 处理 |
|------|------|
| Admin 启动 `Port 8080 already in use` | 释放 8080；StrictV1 不接受 8082 代理 |
| E2E `boot-local` 失败 | 查 MySQL 连接、Flyway 是否卡死、Admin job 日志 |
| perf 门禁超时 | 单独跑 `run-perf-gate.ps1`；确认 Demo Netty 20550 在线 |
| `v1-acceptance` Exit 0 但 note=skipped | **不算通过**；去掉所有 `-Skip*` 重跑 |
| 本地 SSO 用 5173 | 仅影响 dev；StrictV1 走 Admin:8080 内嵌静态，与 Vite 无关 |

---

## 7. 与 CI 的关系

- **CI 默认**：Layer A 白盒（`mvn test`）
- **发版前人工/专用机**：本指南全量 StrictV1（资源消耗大，不建议每条 PR 跑）

---

<p align="center"><sub>StrictV1 通过后即可进入 RC → <code>v1.0.0</code> tag 发版流程</sub></p>
