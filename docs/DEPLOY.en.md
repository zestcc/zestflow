# ZestFlow Public Deployment Guide

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** How-to · **Language** English · [简体中文](DEPLOY.md) · [← Documentation hub](README.en.md)

Applies to: **personal open-source v0.1.0** single-node / small-scale public deployment. Modeled after xxl-job's "scheduler center + executor internal network isolation" pattern.

---

## 1. Architecture and ports

```text
                    [ Public Internet ]
                            │
                       Nginx/Caddy
                       TLS :443
                            │
                       Admin :8080  ← only HTTP entry recommended for public exposure
                            │
        ┌───────────────────┼───────────────────┐
        │  Internal / 127.0.0.1 / VPC             │
        │  Executor Netty :20550                  │
        │  Collector Netty :20650                 │
        │  Business Tomcat :8081 (optional)       │
        └─────────────────────────────────────────┘
```

| Port | Component | Public |
|------|-----------|--------|
| 8080 | Admin (UI + API) | Yes (via reverse proxy + TLS) |
| 20550 | Executor Netty | **No** |
| 20650 | Collector Netty | **No** |
| 8081 | Business Spring Boot | **No** (`server.address=127.0.0.1`) |

---

## 2. Quick deployment (single node)

### 2.1 Preparation

- JDK 17+, MySQL 8+
- Three databases: `zestflow_admin`, `zestflow_app_bussiness`, `zestflow_app_log`
- **Admin deployment package** does not include init-db / init.sql; run `CREATE DATABASE zestflow_admin` yourself
- Edit `config/application-prod.yml` `spring.datasource` credentials, then `./start-admin.sh start` (Flyway migrates automatically)
- **Flyway in prod:** `baseline-on-migrate` supports existing databases; add incremental DDL only as new `V{n}__*.sql`

### 2.2 Generate secrets

**Option A — One-click deployment package (recommended)**

```powershell
mvn package -pl zestflow-admin -Pdist -DskipTests
# or
powershell -File scripts/deploy/package-admin.ps1
```

Artifacts under `deploy/`:

| Path | Description |
|------|-------------|
| `zestflow_admin_{version}_linux/` | Linux directory (includes start-admin.sh) |
| `zestflow_admin_{version}_linux.tar.gz` | Linux archive (tar.gz) |
| `zestflow_admin_{version}_win/` | Windows directory (includes start-admin.bat) |
| `zestflow_admin_{version}_win.zip` | Windows archive |
| `zestflow_admin_{version}_win.zip` | Windows archive |

**Option B — Public demo package (Admin + Demo same host, Linux only)**

```powershell
cd zestflow-admin-ui && pnpm build
mvn package -pl zestflow-admin,zestflow-demo -am -Pdemo-dist -DskipTests
# or
powershell -File scripts/deploy/package-demo.ps1
```

| Path | Description |
|------|-------------|
| `zestflow_admin_demo_{version}_linux/` | Admin (profile=demo, Playground + IP demo) |
| `zestflow_admin_demo_{version}_linux.tar.gz` | Admin Linux archive |
| `zestflow_demo_demo_{version}_linux/` | Demo (Executor+Collector, tokens paired with Admin) |
| `zestflow_demo_demo_{version}_linux.tar.gz` | Demo Linux archive |

Create `zestflow_admin`, `zestflow_app_bussiness`, `zestflow_app_log` on the same MySQL instance; set the same host/user/password in both `application-demo.yml` files. See `README.txt` inside the package.

Auto-generated under `config/`: `secret`, `registry-token`, `executor-access-token`, `collector.access-token`, `application-secrets.yml`, `bootstrap-admin.password`.

Default database: `127.0.0.1` / `root` / `root`; mail disabled; `SPRING_PROFILE=prod`.

**Option C — Manual**

```powershell
# Example: PowerShell 32-byte Base64
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

Generate and **record** three independent values:

- `registry-token` (Admin ↔ Executor/Collector registration)
- `executor-access-token` (Admin → Executor Netty)
- `collector-access-token` (Admin → Collector queries)
- `jwt.secret` (≥32 characters)
- Bootstrap admin password (not `admin123`)

### 2.3 Admin

```powershell
copy zestflow-admin\src\main\resources\application-prod.example.yml `
     zestflow-admin\src\main\resources\application-prod.yml
# Edit application-prod.yml: database, all change-me-* placeholders, mail, etc.

mvn package -pl zestflow-admin -DskipTests
java -jar zestflow-admin\target\zestflow-admin-0.1.0.jar --spring.profiles.active=prod
```

**Production startup guard** (`AdminProductionGuard`) automatically validates:

- All three machine tokens configured and ≥16 characters
- JWT is not the dev default
- Blocks `admin123`, `playground.enabled=true`, `ip-demo-mode=enabled`
- Blocks `springdoc.api-docs.enabled=true`, `springdoc.swagger-ui.enabled=true` (enforced by `AdminProductionGuard`)

### 2.4 Business application (embedded Executor + Collector)

```powershell
copy zestflow-demo\src\main\resources\application-prod.example.yml `
     zestflow-demo\src\main\resources\application-prod.yml
# registry-token / access-token must match Admin

mvn package -pl zestflow-demo -DskipTests
java -jar zestflow-demo\target\zestflow-demo-0.1.0.jar --spring.profiles.active=prod
```

### 2.5 Reverse proxy (Nginx example)

Admin REST API uses the unified prefix **`/api/zestflow`** (constant in `AdminApiPaths`). Public Nginx should allow only this prefix; return 404 for other `/api/*` scan paths.

Full example: [deploy/nginx-zestflow.cn.example.conf](./deploy/nginx-zestflow.cn.example.conf). Core snippet:

```nginx
# Block scan noise like /api/lottery
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
    # ... same headers (SPA + static assets)
}
```

Production recommendation: **`server.address: 127.0.0.1`**; firewall opens only 80/443, not 8080.

Admin config: `zestflow.mail.base-url: https://flow.example.com`

---

## 3. Token pairing

| Admin config | Executor config | Collector config |
|--------------|-----------------|------------------|
| `zestflow.admin.registry-token` | `zestflow.executor.registry-token` | `zestflow.collector.registry-token` |
| `zestflow.admin.executor-access-token` | `zestflow.executor.access-token` | — |
| `zestflow.collector.access-token` | — | `zestflow.collector.access-token` |

---

## 4. Pre-release self-check

```powershell
# 1) Templates must not contain dangerous defaults
powershell -File scripts/deploy/verify-prod-templates.ps1

# 2) Unit tests
powershell -File scripts/blackbox/run-enterprise-gate.ps1 -SkipRuntimeE2e

# 3) Local full path (dev profile)
powershell -File scripts/blackbox/run-full-e2e.ps1 -E2eProfile fullGreen

# 4) Machine auth (security-e2e profile, after restarting all three processes)
powershell -File scripts/blackbox/run-security-token-e2e.ps1
```

---

## 5. Security checklist

- [x] Production startup guard (code-enforced)
- [ ] Change bootstrap admin password; if `mustChangePassword=1` on first login, change password immediately
- [ ] Firewall: only 443/80 public; 8080 on 127.0.0.1 only; 20550/20650/8081 internal
- [ ] TLS terminated at reverse proxy
- [ ] Strong MySQL password + internal network access only
- [ ] Regular backup of all three databases

Development (`local` profile): default tokens empty, `admin123` allowed — **do not use directly on the public internet**.

---

## 6. Related documentation

- [RELEASE_READINESS.md](./RELEASE_READINESS.en.md) — Quality gate tiers
- [BLACKBOX_TEST_REPORT.md](./BLACKBOX_TEST_REPORT.en.md) — Black-box topology
- [PUBLISH_HANDOFF.md](./PUBLISH_HANDOFF.en.md) — Maven Central first release

---

## 7. Low-memory co-located deployment (4G VPS: MySQL + Admin + Demo)

For small Hong Kong / overseas VPS: **MySQL, Admin, and Demo on one 4 GB machine**. Incorrect memory allocation causes MySQL disconnects, Admin save failures, and Demo reconnect loops — often showing JDBC `Communications link failure` + `EOFException` (`last packet ... 2 milliseconds ago`). Root cause is usually **MySQL process restart / OOM / misconfiguration**, not Hikari alone.

### 7.1 Recommended memory budget

| Component | 4G co-located | 8G+ / external DB |
|-----------|---------------|-------------------|
| MySQL `innodb_buffer_pool_size` | **512M** | 768M ~ 1G |
| Admin JVM (`JVM_XMX`) | **768m** | 1g |
| Demo JVM (`JVM_XMX`) | **768m** | 1g |
| OS reserve | ~1G | ~1G |

### 7.2 MySQL

Template: [deploy/my.cnf.co-located.example](./deploy/my.cnf.co-located.example)

```bash
cp docs/deploy/my.cnf.co-located.example /etc/my.cnf
# Adjust basedir/datadir/port; sync JDBC URL port if not 3306
mysqld --defaults-file=/etc/my.cnf --validate-config
systemctl daemon-reload
systemctl reset-failed mysqld   # if start-limit was hit
systemctl restart mysqld
mysql -h127.0.0.1 -P3306 -uroot -p -e "SHOW VARIABLES LIKE 'innodb_buffer_pool_size';"
```

**Common pitfalls:**

- Line 1 of `/etc/my.cnf` must be `[mysqld]` (no BOM, no bare parameters before it) → otherwise `Found option without preceding group`
- **Do not** shrink `innodb_log_file_size` on an existing database
- On 4G machines **do not** set `innodb_buffer_pool_size=1G`

### 7.3 Admin / Demo JVM

`config/start-admin.env`, `config/start-demo.env` (repo includes 512m/768m + G1 templates):

```bash
JVM_XMS=512m
JVM_XMX=768m
JVM_GC=g1
```

After changes: `./start-admin.sh restart`, `./start-demo.sh restart`.

### 7.4 Hikari (Admin primary + Demo triple pool)

Admin `application-prod.yml`:

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

Demo **Executor / Collector separate pools** (`zestflow.executor.datasource` / `zestflow.collector.datasource`) get the same Hikari defaults via `ZestFlowDataSourcePropertiesResolver`, falling back to `spring.datasource.hikari.*`; if no dedicated URL is set, they share the primary database host.

If MySQL is not on 3306 (e.g. 2882), **JDBC URL port must match my.cnf**.

### 7.5 Troubleshooting quick reference

| Symptom | Check first |
|---------|-------------|
| Random save failures + Hikari validate WARN | `systemctl status mysqld`, `tail .../data/*.err` |
| Demo reconnects every ~90s | Is Admin 8080 responding; did MySQL just restart |
| `EOFException read 0 bytes` | MySQL error log for `ready for connections` at same second (restart) |
| `mysqld.service failed` | `mysqld --validate-config`; my.cnf syntax |

```bash
ss -lntp | grep mysql
tail -50 /opt/module/mysql/mysql8/data/*.err
free -h && df -h
dmesg -T | grep -iE "oom|killed" | tail -10
curl -sf http://127.0.0.1:8080/actuator/health
```

### 7.6 Startup order

```text
MySQL → Admin → Demo
```

Starting Admin/Demo before MySQL is ready causes connection storms; **restart** Java processes after MySQL is healthy.
