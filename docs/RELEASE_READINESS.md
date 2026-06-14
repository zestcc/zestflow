# ZestFlow 开源发布就绪清单

> **版本** 1.0.0 · **更新** 2026-06-14 · **类型** 发布 · [← 文档中心](README.md) · [English](RELEASE_READINESS.en.md)
> 目标：在开源收尾前，用可重复脚本证明**主链路可靠**；公网部署须 `prod` profile + [DEPLOY.md](./DEPLOY.md)。

---

## 1. 诚实边界

| 承诺 | 可达成 | 说明 |
|------|--------|------|
| 主链路 demo 全场景 E2E 全绿 | ✅ | `run-full-e2e.ps1 -E2eProfile fullGreen`，38 场景含 75 步 |
| 单元测试全模块通过 | ✅ | `run-enterprise-gate.ps1` 跑 common / executor / zestflow-demo / collector-jdbc / admin |
| 多租户 JWT 切换隔离 | ✅ | 需 `enterprise-e2e` profile + `run-tenant-multi-e2e.ps1` |
| IP 演示租户隔离 | ✅ | 同上 profile + `run-ip-demo-e2e.ps1`（已修复 IP 匿名无法看场景） |
| 绝对 0 bug | ❌ | 任何系统都无法数学证明；用门禁 + 分层 E2E 逼近 |
| 生产全开关一次跑完 | ⚠️ | registry-token / playground 关闭等需**独立 profile 重启** |

---

## 2. 三层验收模型

```text
Layer A  单元测试（CI 必过）
         mvn test -pl zestflow-admin,zestflow-executor,zestflow-demo,collector-jdbc -am

Layer B  默认运行时黑盒（单租户 single + demo 全场景）
         run-full-e2e.ps1 -E2eProfile fullGreen

Layer C  企业扩展（multi + IP 演示，需重启 Admin）
         profile: local,enterprise-e2e
         run-tenant-multi-e2e.ps1 + run-ip-demo-e2e.ps1
```

**一键门禁**（推荐发布前本地执行）：

```powershell
$env:JAVA_HOME = "D:\IT\JAVA\JAVA17"
cd D:\WORK\Project\zestflow

# 1) 默认服务（single 模式）已启动 :8080 / :20550 / :20650
powershell -File .\scripts\blackbox\run-enterprise-gate.ps1

# 2) 完整企业 profile（重启 Admin 后）
# mvn spring-boot:run -pl zestflow-admin -Dspring-boot.run.profiles=local,enterprise-e2e
powershell -File .\scripts\blackbox\run-enterprise-gate.ps1 -RequireEnterpriseProfile
```

---

## 3. 执行链条覆盖矩阵

| 环节 | 覆盖方式 | 脚本/测试 |
|------|----------|-----------|
| 登录 / JWT / RBAC | Layer B | full-e2e auth + security |
| Admin 模块 CRUD 列表 | Layer B | full-e2e admin 探测 |
| 试验场 → Netty → 链执行 | Layer B | 38 playground execute |
| 业务 API 转发 | Layer B | handleApplyAfterSale |
| 事件采集 / 日志查询 | Layer B | collector health + logs query |
| Executor 注册心跳 | 日志 + 单元 | RegistryServiceImplTest |
| CONTINUE 失败策略 | Layer B | SCN20260602000001 |
| 多租户行级隔离 | Layer C | SCN20260602000002 租户 B 专属 |
| IP → 租户映射 | Layer C | X-Forwarded-For 10.0.0.101/102 |
| 调度 trigger | Layer B | full-e2e + **幂等键** `schedule-{id}-cron-{fireMs}` |
| Admin 集群 ShedLock | 单元 + cluster 构建 | OfflineMonitor / TenantCleanup / ChainSync 全任务 |
| Collector 8 路由 | 单元 | CollectorServerHandlerTest (32) |

**仍建议手工或下一迭代自动化**：无（Layer B 已含 playground 关闭探测脚本）。

**E2E 已覆盖（Layer B）**：链 **publish + active-codes**（`run-chain-publish-e2e.ps1`）、**全生命周期 create→publish→execute**（`run-chain-lifecycle-e2e.ps1`）、**RBAC 越权边界**（`run-rbac-horizontal-e2e.ps1`）、调度 trigger、Actuator 健康（含 HTTP 探活）、deploy-mode/cache 探测、仪表盘运行时拓扑（需 `npm run build` 同步 static）。

**Layer C — security-e2e Profile**（需重启 Admin + Executor + Collector）：`registry-token` / `executor-access-token` / `collector access-token` 401 成对验证（`run-security-token-e2e.ps1`；门禁 `-RequireSecurityProfile`）。

**Layer C — playground-disabled-e2e**：`run-playground-disabled-e2e.ps1`；门禁 `-RequirePlaygroundDisabledProfile`。

**Layer D — 生产级验收（2026-06）**：`run-production-acceptance.ps1` 四层门禁（白盒 + 黑盒 + 主链路 + 压力）。详见 [PRODUCTION_ACCEPTANCE.md](./guides/PRODUCTION_ACCEPTANCE.md)。

---

## 4. 可选 E2E Profile

### 4.1 enterprise-e2e / demo（多租户 + IP 试玩）

文件：

- `zestflow-admin/src/main/resources/application-enterprise-e2e.yml`（E2E 门禁）
- `zestflow-admin/src/main/resources/application-demo.example.yml`（公网试玩示例，复制为 `application-demo.yml`）

```yaml
zestflow:
  tenant:
    mode: multi
    ip-demo-mode: enabled
```

启动 Admin E2E：`profiles=local,enterprise-e2e` → `run-enterprise-gate.ps1 -RequireEnterpriseProfile`

公网试玩：`profiles=demo`（**勿与 prod 同开**）。首次访问 IP 自动调用统一 `TenantProvisioner` 创建 trial 租户并全盘克隆母版数据。

**建库/灌库**（仅两种脚本）：

```powershell
powershell -File scripts/init.ps1      # DDL：admin + executor + collector
powershell -File scripts/initData.ps1  # DML：种子数据
```

已有库需删库重建后重跑上述脚本（未正式发布前推荐）；`init.sql` 已含 V3–V5 全部结构变更。

公开开户（可选）：`POST /api/public/tenants/provision`（需 `public-provision-enabled: true`）。

### 4.2 security-e2e（机器鉴权成对）

文件：

- `zestflow-admin/src/main/resources/application-security-e2e.yml`
- `zestflow-demo/src/main/resources/application-security-e2e.yml`

```yaml
# Admin
zestflow.admin.registry-token: e2e-security-registry-token
zestflow.admin.executor-access-token: e2e-security-executor-token
zestflow.collector.access-token: e2e-security-collector-token

# Executor（须一致）
zestflow.executor.access-token: e2e-security-executor-token
zestflow.executor.registry-token: e2e-security-registry-token

# Collector JDBC
zestflow.collector.access-token: e2e-security-collector-token
```

启动（**Admin + Executor + Collector 都要重启**）：

```powershell
# Admin
mvn spring-boot:run -pl zestflow-admin -Dspring-boot.run.profiles=local,security-e2e

# Executor
mvn spring-boot:run -pl zestflow-demo -Dspring-boot.run.profiles=local,security-e2e

# Collector JDBC
mvn spring-boot:run -pl zestflow-collector/collector-jdbc -Dspring-boot.run.profiles=local,security-e2e

# 验证
.\scripts\blackbox\run-security-token-e2e.ps1
.\scripts\blackbox\run-enterprise-gate.ps1 -SkipMavenTest -RequireSecurityProfile
```

---

## 5. enterprise-e2e / IP 试玩种子说明

种子数据（`initData.sql`）：

- 租户 B `id=2`，admin 绑定 `user_tenant`
- `tenant_ip_mapping`: `10.0.0.101→2`, `10.0.0.102→1`（E2E 预埋；新 IP 由 `TenantProvisioner` 自动创建 trial 租户）
- 场景 `SCN20260602000002` 仅 `tenant_id=2`

**注意**：IP 隔离依赖 `mode=multi`（MyBatis-Plus 租户行插件）；仅开 ip-demo 而 single 无效。业务唯一约束均为 `(tenant_id, …)`，见 `init.sql`。

---

## 6. 安全与可靠性

| 项 | 默认开发 | 生产建议 |
|----|----------|----------|
| Playground | 需 JWT + RBAC | **`enabled=false`**（prod 守卫强制） |
| Registry | token 空=放行 + WARN | **`registry-token` 必填**（prod 守卫强制） |
| Executor Netty | access-token 可选 | **与 Admin 成对配置**（prod 守卫强制） |
| Collector | access-token 可选 | **与 Admin 成对配置**（prod 守卫强制） |
| JWT | application.yml dev 值 | **≥32 字符随机串**（prod 守卫强制） |
| 默认管理员 | admin/admin123 | **强口令 + 首次改密**（prod 禁止 admin123） |
| IP 演示 | enterprise-e2e 专用 | **禁止**生产 enabled（prod 守卫强制） |

性能：Layer B 75 步链在本机 ~75ms；压测用 `run-blackbox.ps1` QPS 段（非门禁必过）。

---

## 7. 发布前 Checklist

- [ ] GitHub **CI**（Layer A）全绿：`.github/workflows/ci.yml`（**首次 push 后**在 Actions 查看）
- [x] `run-enterprise-gate.ps1` Layer A+B 全 PASS（本地，2026-06-02）
- [x] `scripts/deploy/verify-prod-templates.ps1` — prod 模板无 admin123 / playground 开启
- [x] **prod 启动守卫** — Admin / Executor / Collector `*ProductionGuard`（令牌、JWT、playground 关闭）
- [x] 灌库：`scripts/init.ps1` + `scripts/initData.ps1`（含租户 B + IP 映射 + 全盘母版种子）
- [x] Admin `enterprise-e2e` + `-RequireEnterpriseProfile` 全 PASS（multi 隔离 + IP 演示）
- [x] Admin 集群 ShedLock：调度 / 离线检测 / 租户清理 / 链同步缓存（`-Pcluster` + `deploy-mode=cluster`）
- [x] Admin→Executor 幂等键贯通（调度 cron/manual、试验场）
- [x] 嵌入模式默认单层 async（starter `zestflow-starter-defaults.properties`）
- [ ] Admin + Executor `security-e2e` + `-RequireSecurityProfile` 全 PASS（registry / executor token）
- [x] 前端改动后 `cd zestflow-admin-ui && npm run build`（产物写入 admin static）
- [x] `mvn package` 全反应堆 `-DskipTests` 通过
- [x] README / 门禁文档已指向 `RELEASE_READINESS.md`
- [ ] Maven Central 首发：`mvn clean deploy -Prelease -DskipTests`（见下方 §8）

---

## 8. Maven Central 首发（cn.zestflow.www）

**发布 artifact（9 个）：** `zestflow`、`zestflow-common`、`zestflow-executor`、`zestflow-starter`、`zestflow-collector`、`collector-core`、`collector-jdbc`、`collector-kafka`、`collector-rabbitmq`

**不发布：** `zestflow-admin`、`zestflow-demo`（`maven-deploy-plugin skip=true`）

### 已完成（代码侧）

- [x] 版本 `0.1.0`、`distributionManagement`、`release` profile
- [x] developer / issueManagement 元数据（`zestcc@126.com`）
- [x] 发布脚本：[`scripts/maven/verify-release.ps1`](../scripts/maven/verify-release.ps1)、[`scripts/maven/publish-central.ps1`](../scripts/maven/publish-central.ps1)
- [x] settings 模板：[`maven/settings.xml.example`](../maven/settings.xml.example)

### 明日待你完成（私钥 + Token）

| 步骤 | 操作 |
|------|------|
| 1 | 安装 [Gpg4win](https://www.gpg4win.org/download.html) |
| 2 | 从原机器导出私钥：`gpg --export-secret-keys 5B28B71AF1128C97 > zestflow-secret.asc` |
| 3 | 本机导入：`gpg --import zestflow-secret.asc` |
| 4 | 验证：`gpg --list-secret-keys --keyid-format LONG`（应有 `sec ... 5B28B71AF1128C97`） |
| 5 | 复制 `maven/settings.xml.example` → `%USERPROFILE%\.m2\settings.xml`，填入 **Central Portal User Token** + **GPG 口令** |
| 6 | 执行正式发布（见下） |

**Central Portal User Token（不是旧 OSSRH 密码）：**

- 入口：https://central.sonatype.com/usertoken
- 或：Account → Generate User Token
- `settings.xml` 里 `<server><id>central</id>`（**不是** `ossrh`）
- Token 只显示一次，务必保存

**GPG 公钥（已上传 keyserver）：**

- UID: `zestflow <zestcc@126.com>`
- Key ID: `5B28B71AF1128C97`
- Fingerprint: `3C3D03110B28D04E5C92B6075B28B71AF1128C97`

### 命令

```powershell
# 需 JDK 17 — 今日可先验证 release 构件（无需 GPG / Token）
powershell -File scripts/maven/verify-release.ps1

# 明日私钥 + settings.xml 就绪后 — 一键发布
powershell -File scripts/maven/publish-central.ps1
```

等价手动命令：

```powershell
$env:JAVA_HOME = "D:\IT\JAVA\JAVA17"   # 按本机路径

# 验证
mvn clean verify -Prelease -DskipTests "-Dgpg.skip=true"

# 发布（勿加 gpg.skip）
mvn clean deploy -Prelease -DskipTests
```

上传后在 [Central Portal → Deployments](https://central.sonatype.com/publishing/deployments) 确认状态为 **Published**（`autoPublish=true` 通常自动完成）。索引可见约数分钟~2 小时。

> **说明：** OSSRH（`oss.sonatype.org`）已于 2025-06 下线；本项目已改用 `central-publishing-maven-plugin` + Central Portal User Token。

---

## CI（GitHub Actions）

| Workflow | 触发 | 内容 |
|----------|------|------|
| [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | push / PR → `main`/`master`/`develop` | Layer A 五模块 `mvn test` + 全量 `package -DskipTests` |
| [`.github/workflows/e2e-manual.yml`](../.github/workflows/e2e-manual.yml) | 手动 | 说明：E2E 需在本地/自建 Runner 跑 `run-enterprise-gate.ps1` |

Badge（仓库 push 后可用）：

```markdown
![CI](https://github.com/zestcc/zestflow/actions/workflows/ci.yml/badge.svg)
```

---

## 8. 相关文档

- [FULL_E2E_TEST_REPORT.md](./FULL_E2E_TEST_REPORT.md)
- [BLACKBOX_TEST_REPORT.md](./BLACKBOX_TEST_REPORT.md)
- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [scripts/blackbox/README.md](../scripts/blackbox/README.md)
