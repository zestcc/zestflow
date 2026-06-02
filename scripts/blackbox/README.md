# 黑盒 / 全流程测试脚本

## 脚本

| 文件 | 用途 |
|------|------|
| `run-blackbox.ps1` | 快速冒烟：健康检查、Netty 业务 API、单场景试验场、可选 QPS |
| `run-full-e2e.ps1` | 全流程：登录、features、租户、Admin 各模块、Collector、**全部试验场场景**、安全矩阵 |
| `e2e-scene-policy.json` | 场景策略：**全绿 / 一部分绿 / 报错跳过** |
| `run-enterprise-gate.ps1` | **发布门禁**：mvn test + fullGreen E2E + 多租户/IP（可选） |
| `run-tenant-multi-e2e.ps1` | 多租户 JWT 隔离（需 `enterprise-e2e` profile） |
| `run-ip-demo-e2e.ps1` | IP → 租户隔离（需 `multi` + `ip-demo-mode=enabled`） |

结果 JSON：`results/blackbox-*.json`、`results/full-e2e-*.json`。

## 运行前提

1. `JAVA_HOME=D:\IT\JAVA\JAVA17`
2. 已启动 `zestflow-admin`（8080）与 `zestflow-executor-test`（Netty 20550、Collector 20650）
3. MySQL 与 `application-local.yml` 已配置；种子已灌：`scripts/seed/Apply-DemoSeed.ps1`

```powershell
$env:JAVA_HOME = "D:\IT\JAVA\JAVA17"
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
```

CI 上仅跑 Layer A（`mvn test`），与门禁脚本前四步一致；Layer B/C 需本地或自建 Runner。

## 文档

- [docs/RELEASE_READINESS.md](../../docs/RELEASE_READINESS.md) — 开源发布三层验收  
- [docs/BLACKBOX_TEST_REPORT.md](../../docs/BLACKBOX_TEST_REPORT.md) — 场景矩阵、压测、安全清单  
- [docs/FULL_E2E_TEST_REPORT.md](../../docs/FULL_E2E_TEST_REPORT.md) — 全流程 + 多租户 + 配置矩阵  
- [scripts/seed/DEMO_CHAIN_MATRIX.md](../seed/DEMO_CHAIN_MATRIX.md) — demo 链分档与节点类型
