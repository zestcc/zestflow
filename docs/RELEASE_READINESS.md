# ZestFlow 开源发布就绪清单

> **版本** V1.0-SNAPSHOT · **更新** 2026-06-02  
> 目标：在开源收尾前，用可重复脚本证明**主链路可靠**；多租户 / IP 演示 / 安全开关分层验收。

---

## 1. 诚实边界

| 承诺 | 可达成 | 说明 |
|------|--------|------|
| 主链路 demo 全场景 E2E 全绿 | ✅ | `run-full-e2e.ps1 -E2eProfile fullGreen`，38 场景含 75 步 |
| 单元测试全模块通过 | ✅ | `run-enterprise-gate.ps1` 跑 common / executor / executor-test / collector-jdbc / admin |
| 多租户 JWT 切换隔离 | ✅ | 需 `enterprise-e2e` profile + `run-tenant-multi-e2e.ps1` |
| IP 演示租户隔离 | ✅ | 同上 profile + `run-ip-demo-e2e.ps1`（已修复 IP 匿名无法看场景） |
| 绝对 0 bug | ❌ | 任何系统都无法数学证明；用门禁 + 分层 E2E 逼近 |
| 生产全开关一次跑完 | ⚠️ | registry-token / playground 关闭等需**独立 profile 重启** |

---

## 2. 三层验收模型

```text
Layer A  单元测试（CI 必过）
         mvn test -pl zestflow-admin,zestflow-executor,zestflow-executor-test,collector-jdbc -am

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
| 路由策略 | 单元 | RouteStrategyTest |
| Collector 8 路由 | 单元 | CollectorServerHandlerTest (32) |

**仍建议手工或下一迭代自动化**：playground 关闭 404（E2E 在 playground.enabled=false 时已探测）。

**E2E 已覆盖（Layer B）**：链 active-codes、调度 trigger（有种子时）、Actuator `zestflowAdmin` 健康、deploy-mode/cache 探测。

**仍建议手工**：链 CRUD 发布全链路、registry-token 401 成对配置验证。

---

## 4. enterprise-e2e Profile

文件：`zestflow-admin/src/main/resources/application-enterprise-e2e.yml`

```yaml
zestflow:
  tenant:
    mode: multi
    ip-demo-mode: enabled
```

种子数据（`initData.sql`）：

- 租户 B `id=2`，admin 绑定 `user_tenant`
- `tenant_ip_mapping`: `10.0.0.101→2`, `10.0.0.102→1`
- 场景 `SCN20260602000002` 仅 `tenant_id=2`

**注意**：IP 隔离依赖 `mode=multi`（MyBatis-Plus 租户行插件）；仅开 ip-demo 而 single 无效。

---

## 5. 安全与可靠性

| 项 | 默认开发 | 生产建议 |
|----|----------|----------|
| Playground | 需 JWT + RBAC | 可 `enabled=false` |
| Registry | token 空=放行 | 配置 `registry-token` |
| Executor Netty | access-token 可选 | 与 Admin 成对配置 |
| JWT | application-local | 强密钥 + 短 TTL |
| IP 演示 | 仅演示环境 | **禁止**生产 enabled |

性能：Layer B 75 步链在本机 ~75ms；压测用 `run-blackbox.ps1` QPS 段（非门禁必过）。

---

## 6. 发布前 Checklist

- [ ] GitHub **CI**（Layer A）全绿：`.github/workflows/ci.yml`（**首次 push 后**在 Actions 查看）
- [x] `run-enterprise-gate.ps1` Layer A+B 全 PASS（本地，2026-06-02）
- [x] 灌库：`Apply-DemoSeed.ps1`（含租户 B + IP 映射 + `SCN20260602000002`）
- [x] Admin `enterprise-e2e` + `-RequireEnterpriseProfile` 全 PASS（multi 隔离 + IP 演示）
- [x] `mvn package` 全反应堆 `-DskipTests` 通过
- [x] README / 门禁文档已指向 `RELEASE_READINESS.md`

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

## 7. 相关文档

- [FULL_E2E_TEST_REPORT.md](./FULL_E2E_TEST_REPORT.md)
- [BLACKBOX_TEST_REPORT.md](./BLACKBOX_TEST_REPORT.md)
- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [scripts/blackbox/README.md](../scripts/blackbox/README.md)
