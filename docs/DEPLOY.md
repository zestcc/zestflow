# ZestFlow 公网部署指南

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** How-to · [← 文档中心](README.md) · [English](DEPLOY.en.md)
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
- **Admin 部署包**不含 init-db / init.sql；自行 `CREATE DATABASE zestflow_admin`
- 修改 `config/application-prod.yml` 中 `spring.datasource` 口令后 `./start-admin.sh start`（Flyway 自动 migrate）
- **Flyway prod 开启**：`baseline-on-migrate` 兼容存量库；增量 DDL 只加新 `V{n}__*.sql`

### 2.2 生成密钥

**方式 A — 一键部署包（推荐）**

```powershell
mvn package -pl zestflow-admin -Pdist -DskipTests
# 或
powershell -File scripts/deploy/package-admin.ps1
```

产物位于 `deploy/`：

| 路径 | 说明 |
|------|------|
| `zestflow_admin_{version}_linux/` | Linux 目录（含 start-admin.sh） |
| `zestflow_admin_{version}_linux.zip` | Linux 压缩包 |
| `zestflow_admin_{version}_win/` | Windows 目录（含 start-admin.bat） |
| `zestflow_admin_{version}_win.zip` | Windows 压缩包 |

**方式 B — 公网试玩包（Admin + Demo 同机，仅 Linux 目录）**

```powershell
cd zestflow-admin-ui && pnpm build
mvn package -pl zestflow-admin,zestflow-demo -am -Pdemo-dist -DskipTests
# 或
powershell -File scripts/deploy/package-demo.ps1
```

| 路径 | 说明 |
|------|------|
| `zestflow_admin_demo_{version}_linux/` | Admin（profile=demo，试验场+IP试玩） |
| `zestflow_demo_demo_{version}_linux/` | Demo（Executor+Collector，令牌与 Admin 成对） |

同一 MySQL 实例上建 `zestflow_admin`、`zestflow_app_bussiness`、`zestflow_app_log`，两处 `application-demo.yml` 改同一 host/user/password。详见包内 `README.txt`。

`config/` 内自动生成：`secret`、`registry-token`、`executor-access-token`、`collector.access-token`、`application-secrets.yml`、`bootstrap-admin.password`。

默认数据库：`127.0.0.1` / `root` / `root`；邮件关闭；`SPRING_PROFILE=prod`。

**方式 B — 手动**

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
- 禁止 `springdoc.api-docs.enabled=true`、`springdoc.swagger-ui.enabled=true`（`AdminProductionGuard` 强制）

### 2.4 业务应用（Executor + Collector 嵌入式）

```powershell
copy zestflow-demo\src\main\resources\application-prod.example.yml `
     zestflow-demo\src\main\resources\application-prod.yml
# registry-token / access-token 与 Admin 成对一致

mvn package -pl zestflow-demo -DskipTests
java -jar zestflow-demo\target\zestflow-demo-0.1.0.jar --spring.profiles.active=prod
```

### 2.5 反向代理（Nginx 示例）

Admin REST API 统一前缀 **`/api/zestflow`**（常量见 `AdminApiPaths`）。公网 Nginx 建议仅放行此前缀下的 API，其余 `/api/*` 扫描路径直接 404。

完整示例见 [deploy/nginx-zestflow.cn.example.conf](./deploy/nginx-zestflow.cn.example.conf)，核心片段：

```nginx
# 挡掉 /api/lottery 等扫描噪音
location ~ ^/api/(?!zestflow/) {
    return 404;
}

location /api/zestflow/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location / {
    proxy_pass http://127.0.0.1:8080;
    # ... 同上 header（SPA + 静态资源）
}
```

生产建议 **`server.address: 127.0.0.1`**，防火墙仅开放 80/443，不暴露 8080。

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
- [ ] 防火墙：仅 443/80 对公网；8080 仅 127.0.0.1；20550/20650/8081 内网
- [ ] TLS 在反向代理层终止
- [ ] MySQL 强口令 + 仅内网访问
- [ ] 定期备份三库

开发环境（`local` profile）默认 token 为空、`admin123` 可用 — **勿直接用于公网**。

---

## 6. 相关文档

- [RELEASE_READINESS.md](./RELEASE_READINESS.md) — 质量门禁分层
- [BLACKBOX_TEST_REPORT.md](./BLACKBOX_TEST_REPORT.md) — 黑盒拓扑
- [PUBLISH_HANDOFF.md](./PUBLISH_HANDOFF.md) — Maven Central 首发

---

## 7. 小内存同机部署（4G VPS：MySQL + Admin + Demo）

适用香港/海外小机：**MySQL、Admin、Demo 跑在同一台 4G 内存机器**。内存分配不当会导致 MySQL 断连、Admin 保存失败、Demo 反复重连——表现多为 JDBC `Communications link failure` + `EOFException`（`last packet ... 2 milliseconds ago`），根因通常是 **MySQL 进程重启/OOM/配置错误**，而非 Hikari 单独问题。

### 7.1 推荐内存预算

| 组件 | 4G 同机 | 8G+ / DB 外置 |
|------|---------|---------------|
| MySQL `innodb_buffer_pool_size` | **512M** | 768M ~ 1G |
| Admin JVM (`JVM_XMX`) | **768m** | 1g |
| Demo JVM (`JVM_XMX`) | **768m** | 1g |
| OS 预留 | ~1G | ~1G |

### 7.2 MySQL

模板：[deploy/my.cnf.co-located.example](./deploy/my.cnf.co-located.example)

```bash
cp docs/deploy/my.cnf.co-located.example /etc/my.cnf
# 按实际 basedir/datadir/port 修改；port 非 3306 时同步改 JDBC URL
mysqld --defaults-file=/etc/my.cnf --validate-config
systemctl daemon-reload
systemctl reset-failed mysqld   # 若曾 start-limit
systemctl restart mysqld
mysql -h127.0.0.1 -P3306 -uroot -p -e "SHOW VARIABLES LIKE 'innodb_buffer_pool_size';"
```

**常见踩坑：**

- `/etc/my.cnf` 第 1 行必须是 `[mysqld]`（无 BOM、无前置裸参数）→ 否则 `Found option without preceding group`
- **不要**在已有库上改小 `innodb_log_file_size`
- 4G 机器 **不要**设 `innodb_buffer_pool_size=1G`

### 7.3 Admin / Demo JVM

`config/start-admin.env`、`config/start-demo.env`（仓库已含 512m/768m + g1 模板）：

```bash
JVM_XMS=512m
JVM_XMX=768m
JVM_GC=g1
```

改后：`./start-admin.sh restart`、`./start-demo.sh restart`。

### 7.4 Hikari（Admin 主库 + Demo 三池）

Admin `application-prod.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/zestflow_admin?...&tcpKeepAlive=true
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      idle-timeout: 600000
      max-lifetime: 1740000
      keepalive-time: 120000
```

Demo 的 **Executor / Collector 独立池**（`zestflow.executor.datasource` / `zestflow.collector.datasource`）由 `ZestFlowDataSourcePropertiesResolver` 自动套用相同 Hikari 默认值，并回落 `spring.datasource.hikari.*`；未配专库 URL 时与主库同址。

MySQL 非 3306（如 2882）时，**JDBC URL 端口必须与 my.cnf 一致**。

### 7.5 故障排查速查

| 现象 | 优先检查 |
|------|----------|
| 保存随机失败 + Hikari validate WARN | `systemctl status mysqld`、`tail .../data/*.err` |
| Demo 每 ~90s 重连 | Admin 8080 是否响应；MySQL 是否刚重启 |
| `EOFException read 0 bytes` | MySQL error log 同一秒是否有 `ready for connections`（重启） |
| `mysqld.service failed` | `mysqld --validate-config`；my.cnf 语法 |

```bash
ss -lntp | grep mysql
tail -50 /opt/module/mysql/mysql8/data/*.err
free -h && df -h
dmesg -T | grep -iE "oom|killed" | tail -10
curl -sf http://127.0.0.1:8080/actuator/health
```

### 7.6 启动顺序

```text
MySQL → Admin → Demo
```

MySQL 未就绪时启动 Admin/Demo 会产生连接风暴，恢复后需 **restart** Java 进程。
