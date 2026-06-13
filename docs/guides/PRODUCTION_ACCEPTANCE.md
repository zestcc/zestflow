# 生产级验收（Production Acceptance）

> 对标 xxl-job 发布门禁 + 企业 SaaS 四层验收模型：**白盒 → 黑盒 → 主链路 → 压力**。

## 一键执行

前提：MySQL 已初始化，`zestflow-admin`（8080）、`zestflow-demo`（Netty 20550、Collector 20650）已启动。

```powershell
$env:JAVA_HOME = "D:\IT\JDK17\jdk-17.0.19+10"
cd D:\project\zestflow

# 严格模式（生产发版推荐）
.\scripts\blackbox\run-production-acceptance.ps1

# 无运行时服务时仅跑白盒
.\scripts\blackbox\run-production-acceptance.ps1 -SkipRuntimeBlackbox -SkipLink -SkipStress

# 完整压测 + JMH 门禁
.\scripts\blackbox\run-production-acceptance.ps1 -RequirePerf

# 企业扩展 profile 已重启 Admin 后
.\scripts\blackbox\run-production-acceptance.ps1 -RequireEnterpriseProfile -RequireSecurityProfile
```

报告输出：`scripts/blackbox/results/production-acceptance-*.json`

## 四层模型

| 层 | 内容 | 脚本/测试 |
|----|------|-----------|
| **白盒 Layer A** | 全仓库 `mvn test`（SSO/SSE/读快照/生产守卫单测） | `run-production-acceptance.ps1` Phase whitebox |
| **黑盒 Layer B** | fullGreen E2E、RBAC、链生命周期、SSO/SSE/读快照探测 | 同脚本 blackbox 阶段 |
| **链路 Layer C** | 试验场执行 → 日志入库 → SSE connected/done → 链代理在线 | `run-platform-link-e2e.ps1` |
| **压力 Layer D** | SSE 并发 connected P99 + 可选 JMH perf gate | `run-log-stream-stress-e2e.ps1` + `-RequirePerf` |

SLA 阈值：`scripts/blackbox/production-acceptance-policy.json`

## 分层脚本（可单独跑）

| 脚本 | 用途 |
|------|------|
| `run-production-acceptance.ps1` | **总门禁**（四层汇总 JSON） |
| `run-platform-link-e2e.ps1` | 主链路：playground → logs → SSE → chains → SSO |
| `run-log-stream-stress-e2e.ps1` | SSE 8 并发 connected 延迟 P99 |
| `run-log-live-stream-e2e.ps1` | SSE 功能 + JWT 鉴权 |
| `run-sso-e2e.ps1` | SSO Discovery + Admin 端点 |
| `run-executor-read-cache-e2e.ps1` | Executor 读快照代理 |
| `run-schedule-trigger-e2e.ps1` | E2E-08 调度 trigger 独立脚本 |
| `run-enterprise-gate.ps1` | 原企业门禁（可被 production-acceptance 覆盖） |
| `run-perf-gate.ps1` | JMH + HTTP 并发 + 运行时 QPS |

## 白盒覆盖要点

| 模块 | 测试类 |
|------|--------|
| SSO 门面 | `SsoAuthServiceTest`、`SsoProviderRegistryTest`、`AbstractOidcSsoProviderTest` |
| 日志 SSE | `ExecutionLiveStreamServiceTest`（含并发）、`LogLiveStreamControllerTest`、`ExecutionTraceSupportTest`（common） |
| SSO 回调 | `AbstractOidcSsoProviderTest.handleCallback_*`、`SsoAuthServiceTest` |
| 离线写拦截 | `ExecutorProxyServiceOfflineWriteTest` |
| 401 未登录 | `JwtUnauthorizedEntryPointTest`、`AdminApiSecurityMvcTest` |
| 读快照 | `ExecutorProxyServiceReadCacheTest`、`RedisExecutorReadCacheTest` |
| 生产守卫 | `AdminProductionGuardTest`、`ProductionSecretGuardTest` |

## 生产 profile 手工确认

自动化无法替代以下项（发版前 checklist）：

- [ ] Admin / Executor / Collector 使用 `spring.profiles.active=prod`
- [ ] `registry-token`、`executor-access-token`、`collector.access-token` 非占位符
- [ ] `jwt.secret` ≥ 32 字符且非默认值
- [ ] `playground.enabled=false`
- [ ] SSO 启用时 `client-secret` / `redirect-uri` 已配置
- [ ] 浏览器完成 SSO 回调 + SLO 登出（脚本仅覆盖 API 段）

## 与 CI 的关系

- GitHub/Gitee CI：**Layer A 白盒**（`mvn test`）
- Layer B～D 需本地或自建 Runner 启动 Admin + demo 后执行 `run-production-acceptance.ps1`

详见 [RELEASE_READINESS.md](../RELEASE_READINESS.md)。
