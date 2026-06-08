# ZestFlow 全流程 E2E 测试报告

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** 测试报告 · [← 文档中心](README.md)

| 项目 | 内容 |
|------|------|
| 版本 | 0.1.0 |
| 测试日期 | 2026-06-02 |
| 类型 | 黑盒全流程 + 配置矩阵说明 + 多租户探测 |
| 自动化脚本 | `scripts/blackbox/run-full-e2e.ps1`（全量试验场）、`scripts/blackbox/run-blackbox.ps1`（快速冒烟） |
| 详细黑盒报告 | [BLACKBOX_TEST_REPORT.md](./BLACKBOX_TEST_REPORT.md) |
| 最新结果样例 | 本地运行后生成于 `scripts/blackbox/results/`（不入库） |

---

## 1. 测试目标

在**真实运行进程**（Admin + zestflow-demo + Collector）上验证：

1. **主链路**：登录 → 各管理模块 API → Executor Netty 健康/业务 API → Collector 健康 → 日志查询  
2. **试验场**：38 个场景批量 `execute`（Admin → Executor Netty → 链或 MVC 转发，**禁止直连 Tomcat 8081**）  
3. **多租户**：租户列表、切换租户、租户 CRUD 列表（单租户模式下 API 仍应可用）  
4. **安全配置矩阵**：JWT 缺失/伪造、Registry Token 开发放行等（**需重启**的开关见第 4 节）  
5. **配置开/关**：运行时探测 + 文档化重启验证步骤  

---

## 2. 环境与启动

```text
脚本/浏览器 ──JWT──▶ Admin :8080
                    ├──▶ Executor Netty :20550（链 /api/* 转发）
                    └──▶ Collector :20650（日志）

zestflow-demo：
  Tomcat 127.0.0.1:8081（仅本机，不对 Admin 暴露）
  Netty 0.0.0.0:20550（对外唯一执行通道）
```

| 项 | 要求 |
|----|------|
| `JAVA_HOME` | `D:\IT\JAVA\JAVA17`（勿指向 `...\JDK` 子目录） |
| Admin | `mvn spring-boot:run -pl zestflow-admin` |
| Executor 测试应用 | `mvn spring-boot:run -pl zestflow-demo` |
| MySQL | `application-local.yml`（勿提交密码） |

### 一键执行全流程

```powershell
$env:JAVA_HOME = "D:\IT\JAVA\JAVA17"
cd D:\WORK\Project\zestflow
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\blackbox\run-full-e2e.ps1
# 含 50 步长链等耗时场景：
powershell -File .\scripts\blackbox\run-full-e2e.ps1 -E2eProfile fullGreen -SceneTimeoutSec 300
```

结果写入 `scripts/blackbox/results/full-e2e-{时间戳}.json`。

---

## 3. 本轮自动化结果摘要（2026-06-02）

### 3.1 功能探测（`run-full-e2e.ps1`）

| 类别 | 用例 | 结果 | 说明 |
|------|------|------|------|
| auth | 登录 / userinfo | 通过 | admin / admin123 |
| config | `/api/system/features`（**需 JWT**） | 通过 | `playground.enabled=true` |
| tenant | 我的租户 / 租户列表 / switch-tenant/1 | 通过 | 单租户默认 `tenant_id=1` |
| admin | dashboard、executors、collectors、chains、designs、components、schedules、users、roles、dict、logs | 通过 | 列表类 GET |
| executor | Netty `/health`、业务 API | 通过 | `handleApplyAfterSale` + applyId |
| collector | `GET /collector/health` | 通过 | :20650（非 `/health`） |
| playground | list-all（32 场景） | 通过 | |
| playground | 批量 execute（SkipHeavy） | **28/32 通过**（2026-06-02 16:21） | 4 个链场景 HTTP 500（设计图占位节点无元件绑定）；售后 API 已修复路径校验 |
| blackbox 冒烟 | `run-blackbox.ps1` | **15/17** | 限流 35 连打未触发 429（待查 RateLimiter）；错误密码为 body.code≠200 |
| security | 伪造 JWT → `/api/dashboard/stats` | 通过 | HTTP 403 |
| security | 无 JWT 访问 dashboard | 通过 | HTTP **403** |
| security | Registry 无 Token | 通过 | 开发环境 token 为空时放行 |

### 3.2 试验场场景矩阵

- 种子数据：`zestflow-admin/src/main/resources/db/initData.sql`（32+ 场景）  
- 路径规范：仅 `/execute` 或 `/api/**`；历史库若存 `http://localhost:8081/api/...`，`PlaygroundServiceImpl.normalizeRequestPath` 会自动截取为相对路径  
- 成功判定：HTTP 200 且 `Result.data.status == 1`（业务执行成功）  

完整 38 场景分类见 [BLACKBOX_TEST_REPORT.md §5](./BLACKBOX_TEST_REPORT.md)。

### 3.3 单元测试（编译期）

```bash
mvn test -pl zestflow-admin,zestflow-executor -am
```

含 `NettyMvcDispatcherTest`、`PlaygroundRequestPathValidatorTest`、`ExecutorProxyServiceTest`、试验场相关 Controller/Service 测试。

---

## 4. 配置开/关矩阵（必须重启验证）

以下开关**无法在同一进程内热切换**，需改配置后重启对应服务，再跑 `run-full-e2e.ps1` 或手工用例。

| 配置项 | 默认（开发） | 开启 (ON) 预期 | 关闭 (OFF) 预期 | 验证方法 |
|--------|--------------|----------------|-----------------|----------|
| `zestflow.playground.enabled` | `true`（local） | `/api/playground/**` 200 | Bean 不加载 → **404** | features API + `GET .../scenes/list-all` |
| `zestflow.mail.enabled` | 常 `false` | `SmtpMailService` 发信 | `NoopMailService` 仅日志 | 注册/忘记密码/创建用户（看日志） |
| `zestflow.admin.registry-token` | 空 | 错 Token → **401** | 空=开发放行 | `POST /api/registry/register` |
| `zestflow.admin.executor-access-token` | 空 | Admin 代理带 `X-Access-Token` | 与 executor 一致才通 | 试验场 execute / 链代理 |
| `zestflow.executor.access-token` | 空 | Netty 校验 Token | 空=放行 | 直连 Netty POST |
| `zestflow.tenant.mode` | `single` | `multi` 启用 MP 租户行插件 | 不过滤 `tenant_id` | 重启后创建第二租户 + 数据隔离 |
| `zestflow.tenant.ip-demo-mode` | `disabled` | `enabled` 时 `TenantIpFilter` 按 IP 映射租户 | 不启用过滤器 | 不同 IP 访问看 `tenant_id` |
| `zestflow.admin.deploy-mode` | `standalone` | cluster 启用 Redis 运行时状态 | 单机默认内存，**无需 Redis** | 多 Admin 副本时改为 cluster + `spring.data.redis.*` |
| `zestflow.admin.cache.type` | `caffeine` | `redis` 启用 RedisCacheManager | 单机默认 Caffeine | 与 deploy-mode 独立；多副本建议 redis |

### 4.1 试验场关闭示例

`application-local.yml` 或环境变量：

```yaml
zestflow:
  playground:
    enabled: false
```

重启 Admin 后：

```powershell
# 应 404
Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/playground/scenes/list-all" -Headers @{Authorization="Bearer <token>"}
```

### 4.2 多租户 (multi) 示例

```yaml
zestflow:
  tenant:
    mode: multi
```

重启 Admin 后建议手工验证：

1. `GET /api/tenants` 创建租户 B  
2. 用户仅绑定租户 B → 登录后 `GET /api/chains` 不应看到租户 A 数据  
3. `POST /api/auth/switch-tenant/{id}` 切换后列表数据随租户变化  

当前自动化仅覆盖 **API 可达性**（`switch-tenant/1` 在单租户下返回 200）。

### 4.3 Registry / Executor Token 成对开启

Admin `application-local.yml`：

```yaml
zestflow:
  admin:
    registry-token: "e2e-registry-secret"
    executor-access-token: "e2e-exec-secret"
```

zestflow-demo：

```yaml
zestflow:
  executor:
    access-token: "e2e-exec-secret"
```

重启后：错误 `X-Access-Token` / Registry 头应 **401**；试验场与链代理需带正确 Token。

### 4.4 E2E 场景策略（记住此处：全绿 / 一部分绿 / 报错跳过）

策略文件：`scripts/blackbox/e2e-scene-policy.json`，由 `run-full-e2e.ps1` 加载。

| Profile | 用途 | 命令示例 |
|---------|------|----------|
| **fullGreen**（默认） | 全部 required 场景必须通过；**不**默认跳过 75 步压力链 | `.\scripts\blackbox\run-full-e2e.ps1 -E2eProfile fullGreen -SceneTimeoutSec 300` |
| **partialGreen** | 额外覆盖 `SCN20260602000001`：链 `CHN_DEMO_CONTINUE_ON_ERROR`（`errorStrategy=CONTINUE`，节点 `failStep` 失败仍链成功） | `-E2eProfile partialGreen` |
| **skipOnError** | `optionalScenes` 失败记为 skipped，exit 仍可为 0；profile 默认 `-SkipHeavyScenes` | `-E2eProfile skipOnError` |

报告 JSON 字段：`e2ePolicy`、`sceneSummary.requiredPass/Fail`、`optionalSkipped`、`heavySkipped`。required 失败 → **exit 1**。

与调度「路由策略」无关；链级失败策略见引擎 `errorStrategy`（STOP / CONTINUE）。

---

## 5. 多租户相关 API 清单

| API | 鉴权 | 单租户 | multi 模式额外行为 |
|-----|------|--------|-------------------|
| `GET /api/auth/tenants` | JWT | 返回用户可访问租户 | 同左 |
| `POST /api/auth/switch-tenant/{id}` | JWT | 切换上下文 | 影响后续查询 `tenant_id` |
| `GET /api/tenants` | JWT + 权限 | 超管可见全部 | 创建/编辑租户 |
| 业务表 CRUD | JWT + `tenant_id` 列 | 默认 `tenant_id=1` | MyBatis-Plus 租户插件自动拼接条件 |

`tenant_id` / `app_code` 由 `TenantAppContext` + `MetaObjectHandler` / Service 写入，详见 `CLAUDE.md` 数据审计规范。

---

## 6. 安全与架构结论（E2E 相关）

| 项 | 结论 |
|----|------|
| 试验场 → 业务 | 仅 `ExecutorProxyService` → Netty，**无** Admin 直连 `host:8081` |
| `/api/auth/**` | `permitAll`；测 JWT 必须用 `/api/dashboard/**` 等受保护路径 |
| 无 JWT | 实测 **403**（非 401） |
| 端点列表 | 不含 Tomcat URL |
| 历史 `request_path` 绝对 URL | 执行层已归一化为 `/api/...` |

---

## 7. 已知问题与后续

| ID | 问题 | 状态 |
|----|------|------|
| E2E-01 | 种子场景 `SCN20260601000229` 默认 `applyId` 为空导致 400 | 脚本 `Prepare-SceneBody` 已补；建议更新 `initData.sql` 默认体 |
| E2E-02 | 配置关（playground off、token on、multi）需**重启**才能自动化 | 文档化（本节 §4）；可增 `application-e2e-off.yml` profile |
| E2E-03 | 多租户**数据隔离** | `run-tenant-multi-e2e.ps1` + profile `enterprise-e2e` |
| E2E-06 | IP 演示隔离 | `run-ip-demo-e2e.ps1` + 同上 profile；见 [RELEASE_READINESS.md](./RELEASE_READINESS.md) |
| E2E-04 | 重场景 `SCN20260531000004`（75 步） | **fullGreen** 默认不 SkipHeavy；冒烟用 `-E2eProfile skipOnError` |
| E2E-05 | 场景策略三档 | `e2e-scene-policy.json` + §4.4；`partialGreen` 场景 `SCN20260602000001` |

---

## 8. 复现检查清单

- [ ] Admin :8080、Netty :20550、Collector :20650 进程存活  
- [ ] `mvn test -pl zestflow-admin,zestflow-executor -am` 通过  
- [ ] `run-full-e2e.ps1` Functional 32/32 全绿、试验场 38/38（fullGreen）  
- [ ] `run-blackbox.ps1` 冒烟 + 可选 QPS  
- [ ] （可选）playground.enabled=false 重启验证 404  
- [ ] （可选）registry-token + executor access-token 成对验证 401  
- [ ] （可选）tenant.mode=multi + 双租户隔离  

---

*报告随 `scripts/blackbox/results/*.json` 更新；重大架构变更请同步 [ARCHITECTURE.md](./ARCHITECTURE.md) 与 [BLACKBOX_TEST_REPORT.md](./BLACKBOX_TEST_REPORT.md)。*
