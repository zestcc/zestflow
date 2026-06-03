# ZestFlow 公网部署指南

> 适用：**个人开源项目 v0.1.0** 单机/小规模公网部署。对标 xxl-job「调度中心 + 执行器内网隔离」模型。

---

## 1. 架构与端口

```text
                    [ 公网 ]
                        │
                   Nginx/Caddy
                   TLS :443
                        │
                   Admin :8080  ← 唯一建议公网暴露的 HTTP 入口
                        │
        ┌───────────────┼───────────────┐
        │  内网 / 127.0.0.1 / VPC       │
        │  Executor Netty :20550        │
        │  Collector Netty :20650       │
        │  业务 Tomcat :8081 (可选)     │
        └───────────────────────────────┘
```

| 端口 | 组件 | 公网 |
|------|------|------|
| 8080 | Admin（UI + API） | 是（经反向代理 + TLS） |
| 20550 | Executor Netty | **否** |
| 20650 | Collector Netty | **否** |
| 8081 | 业务 Spring Boot | **否**（`server.address=127.0.0.1`） |

---

## 2. 快速部署（单机）

### 2.1 准备

- JDK 17+、MySQL 8+
- 三个库：`zestflow_admin`、`zestflow_app_bussiness`、`zestflow_app_log`
- 首次建库：执行 `zestflow-admin/src/main/resources/db/init.sql` + `initData.sql`（按需）

### 2.2 生成密钥

```powershell
# 示例：PowerShell 生成 32 字节 Base64
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

分别生成并**记录**（三份独立值）：

- `registry-token`（Admin ↔ Executor/Collector 注册）
- `executor-access-token`（Admin → Executor Netty）
- `collector-access-token`（Admin → Collector 查询）
- `jwt.secret`（≥32 字符）
- 管理员 bootstrap 口令（非 `admin123`）

### 2.3 Admin

```powershell
copy zestflow-admin\src\main\resources\application-prod.example.yml `
     zestflow-admin\src\main\resources\application-prod.yml
# 编辑 application-prod.yml：数据库、全部 change-me-* 占位符、邮件等

mvn package -pl zestflow-admin -DskipTests
java -jar zestflow-admin\target\zestflow-admin-0.1.0.jar --spring.profiles.active=prod
```

**prod 启动守卫**（`AdminProductionGuard`）会自动校验：

- 三台机器令牌已配置且 ≥16 字符
- JWT 非 dev 默认值
- 禁止 `admin123`、禁止 `playground.enabled=true`、禁止 `ip-demo-mode=enabled`

### 2.4 业务应用（Executor + Collector 嵌入式）

```powershell
copy zestflow-executor-test\src\main\resources\application-prod.example.yml `
     zestflow-executor-test\src\main\resources\application-prod.yml
# registry-token / access-token 与 Admin 成对一致

mvn package -pl zestflow-executor-test -DskipTests
java -jar zestflow-executor-test\target\zestflow-executor-test-0.1.0.jar --spring.profiles.active=prod
```

### 2.5 反向代理（Nginx 示例）

```nginx
server {
    listen 443 ssl;
    server_name flow.example.com;
    # ssl_certificate ...

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Admin 配置：`zestflow.mail.base-url: https://flow.example.com`

---

## 3. 令牌成对关系

| Admin 配置 | Executor 配置 | Collector 配置 |
|------------|---------------|----------------|
| `zestflow.admin.registry-token` | `zestflow.executor.registry-token` | `zestflow.collector.registry-token` |
| `zestflow.admin.executor-access-token` | `zestflow.executor.access-token` | — |
| `zestflow.collector.access-token` | — | `zestflow.collector.access-token` |

---

## 4. 发布前自检

```powershell
# 1) 模板不含危险默认值
powershell -File scripts/deploy/verify-prod-templates.ps1

# 2) 单元测试
powershell -File scripts/blackbox/run-enterprise-gate.ps1 -SkipRuntimeE2e

# 3) 本地全链路（dev profile）
powershell -File scripts/blackbox/run-full-e2e.ps1 -E2eProfile fullGreen

# 4) 机器鉴权（security-e2e profile，三进程重启后）
powershell -File scripts/blackbox/run-security-token-e2e.ps1
```

---

## 5. 安全清单

- [x] prod 启动守卫（代码强制）
- [ ] 修改 bootstrap 管理员口令；首次登录若 `mustChangePassword=1` 须改密
- [ ] 防火墙：仅 443/8080 对公网；20550/20650/8081 内网
- [ ] TLS 在反向代理层终止
- [ ] MySQL 强口令 + 仅内网访问
- [ ] 定期备份三库

开发环境（`local` profile）默认 token 为空、`admin123` 可用 — **勿直接用于公网**。

---

## 6. 相关文档

- [RELEASE_READINESS.md](./RELEASE_READINESS.md) — 质量门禁分层
- [BLACKBOX_TEST_REPORT.md](./BLACKBOX_TEST_REPORT.md) — 黑盒拓扑
- [PUBLISH_HANDOFF.md](./PUBLISH_HANDOFF.md) — Maven Central 首发
