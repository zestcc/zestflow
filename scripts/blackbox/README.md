# 黑盒 / 全流程测试脚本

## 脚本

| 文件 | 用途 |
|------|------|
| `run-blackbox.ps1` | 快速冒烟：健康检查、Netty 业务 API、单场景试验场、可选 QPS |
| `run-full-e2e.ps1` | 全流程：登录、features、租户、Admin 各模块、Collector、**全部试验场场景**、安全矩阵 |
| `e2e-scene-policy.json` | 场景策略：**全绿 / 一部分绿 / 报错跳过** |
| `run-enterprise-gate.ps1` | **发布门禁**：mvn test + fullGreen E2E + 多租户/IP + 安全 token（可选） |
| `run-chain-publish-e2e.ps1` | 链 publish → active-codes → 可选 rollback |
| `run-chain-lifecycle-e2e.ps1` | 创建设计/链 → 绑定 → 发布 → Netty `/execute` |
| `run-rbac-horizontal-e2e.ps1` | 无 JWT / 无效 JWT 访问受保护 API 应 401/403 |
| `run-playground-disabled-e2e.ps1` | playground.enabled=false 时 list/execute 404 |
| `run-security-token-e2e.ps1` | registry + executor + collector token 401 成对（需 `security-e2e` profile） |
| `run-registry-token-e2e.ps1` | 兼容入口（仅 registry 段，委托 security 脚本） |
| `run-perf-gate.ps1` | **Phase 2c 性能门禁**：JMH 编排层 P99.9 + 并发 HTTP 压测 + 可选运行时黑盒 |
| `run-full-perf.ps1` | **全盘压测**：perf-gate + P-03 队列 + P-04 轮询 + Netty + Playground + Collector |
| `perf-gate-policy.json` | P99.9 SLA 阈值（引擎 / HTTP / blackbox / phase2b） |
| `run-tenant-multi-e2e.ps1` | 多租户 JWT 隔离（需 `enterprise-e2e` profile） |
| `run-ip-demo-e2e.ps1` | IP → 租户隔离（需 `multi` + `ip-demo-mode=enabled`） |

结果 JSON：本地写入 `results/`（`*.json` 已 gitignore，不入库）。

## 运行前提

1. `JAVA_HOME=D:\IT\JDK17\jdk-17.0.19+10`（需指向含 `bin\java.exe` 的 JDK 根目录）
2. 已启动 `zestflow-admin`（8080）与 `zestflow-executor-test`（Netty 20550、Collector 20650）
3. MySQL 与 `application-local.yml` 已配置；种子已灌：`scripts/seed/Apply-DemoSeed.ps1`

```powershell
$env:JAVA_HOME = "D:\IT\JDK17\jdk-17.0.19+10"
cd D:\WORK\Project\zestflow
.\scripts\blackbox\run-full-e2e.ps1
```

## E2E 场景策略（`e2e-scene-policy.json`）

| Profile | 含义 | 默认 |
|---------|------|------|
| `fullGreen` | 全部 **required** 场景必须通过（含 75 步 `SCN20260531000004`） | **是** |
| `partialGreen` | 在 fullGreen 基础上，额外校验 `SCN20260602000001`（链级 `CONTINUE`，节点失败仍链成功 = **一部分绿**） | |
| `skipOnError` | `permissive`：列在 `optionalScenes` 的场景失败记为 **skipped** 不拦 exit；默认 `-SkipHeavyScenes` | |

```powershell
# 全绿（默认，不跳过压力链）
.\scripts\blackbox\run-full-e2e.ps1 -E2eProfile fullGreen -SceneTimeoutSec 300

# 一部分绿演示链 + 其余全绿
.\scripts\blackbox\run-full-e2e.ps1 -E2eProfile partialGreen

# 冒烟：可选场景失败跳过 + 默认跳过 75 步
.\scripts\blackbox\run-full-e2e.ps1 -E2eProfile skipOnError
```

报告字段：`sceneSummary.requiredPass/Fail`、`optionalSkipped`、`heavySkipped`；失败时 **exit 1**。

## 参数

`run-full-e2e.ps1`：

- `-BaseAdmin` / `-BaseNetty` / `-BaseCollector`：默认 8080 / 20550 / 20650  
- `-E2eProfile`：`fullGreen` \| `partialGreen` \| `skipOnError`（默认读 JSON `defaultProfile`）  
- `-PolicyFile`：自定义策略 JSON 路径  
- `-SkipHeavyScenes`：显式跳过 `heavyScenes`（`fullGreen` 下需传 `-SkipHeavyScenes:$false` 才跑 75 步；`skipOnError` profile 默认 true）  
- `-SceneTimeoutSec`：链场景超时（默认 120；压力链至少 300）

## 配置开/关

需**重启**才能验证的开关（playground、registry-token、tenant.mode 等）见：

**[docs/FULL_E2E_TEST_REPORT.md](../../docs/FULL_E2E_TEST_REPORT.md)** 第 4 节（含 **§4.4 E2E 场景策略**）。

## 企业级发布门禁

见 **[docs/RELEASE_READINESS.md](../../docs/RELEASE_READINESS.md)**（含 **GitHub Actions CI** 说明）。

```powershell
# 默认 profile（Layer A+B）
.\scripts\blackbox\run-enterprise-gate.ps1

# 重启 Admin：-Dspring-boot.run.profiles=local,enterprise-e2e 后
.\scripts\blackbox\run-enterprise-gate.ps1 -RequireEnterpriseProfile

# 重启 Admin + Executor：profiles=local,security-e2e 后
.\scripts\blackbox\run-enterprise-gate.ps1 -SkipMavenTest -RequireSecurityProfile
# 或单独：.\scripts\blackbox\run-security-token-e2e.ps1

# Phase 2c 性能门禁（JMH + 并发压测，不依赖 Admin 运行时）
.\scripts\blackbox\run-perf-gate.ps1

# 全盘压测（需 Admin:8080 + Netty:20550 已启动）
.\scripts\blackbox\run-full-perf.ps1

# 企业门禁 + 性能门禁一并跑
.\scripts\blackbox\run-enterprise-gate.ps1 -RequirePerfProfile
```

**UI 静态资源**：改 `zestflow-admin-ui/` 后须 `npm run build`（Vite 输出到 `zestflow-admin/src/main/resources/static/`）。

CI 上仅跑 Layer A（`mvn test`），与门禁脚本前四步一致；Layer B/C 需本地或自建 Runner。

## 文档

- [docs/RELEASE_READINESS.md](../../docs/RELEASE_READINESS.md) — 开源发布三层验收  
- [docs/BLACKBOX_TEST_REPORT.md](../../docs/BLACKBOX_TEST_REPORT.md) — 场景矩阵、压测、安全清单  
- [docs/FULL_E2E_TEST_REPORT.md](../../docs/FULL_E2E_TEST_REPORT.md) — 全流程 + 多租户 + 配置矩阵  
- [scripts/seed/DEMO_CHAIN_MATRIX.md](../seed/DEMO_CHAIN_MATRIX.md) — demo 链分档与节点类型
