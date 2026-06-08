# ZestFlow Open Source Release Readiness Checklist

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](RELEASE_READINESS.md) · **Type** Release · [← Documentation hub](README.en.md)  
> Goal: Before open-source wrap-up, use repeatable scripts to prove **main path reliability**; public deployment requires `prod` profile + [DEPLOY.en.md](./DEPLOY.en.md).

---

## 1. Honest Boundaries

| Commitment | Achievable | Notes |
|------------|------------|-------|
| Main path demo full-scenario E2E all green | ✅ | `run-full-e2e.ps1 -E2eProfile fullGreen`, 38 scenarios, 75 steps |
| All-module unit tests pass | ✅ | `run-enterprise-gate.ps1` runs common / executor / zestflow-demo / collector-jdbc / admin |
| Multi-tenant JWT switch isolation | ✅ | Requires `enterprise-e2e` profile + `run-tenant-multi-e2e.ps1` |
| IP demo tenant isolation | ✅ | Same profile + `run-ip-demo-e2e.ps1` (fixed IP anonymous cannot view scenes) |
| Absolute zero bugs | ❌ | No system can mathematically prove this; approach via gates + layered E2E |
| All production switches in one run | ⚠️ | registry-token / playground off etc. need **separate profile restart** |

---

## 2. Three-Layer Acceptance Model

```text
Layer A  Unit tests (CI required)
         mvn test -pl zestflow-admin,zestflow-executor,zestflow-demo,collector-jdbc -am

Layer B  Default runtime black-box (single-tenant single + demo full scenarios)
         run-full-e2e.ps1 -E2eProfile fullGreen

Layer C  Enterprise extensions (multi + IP demo, Admin restart required)
         profile: local,enterprise-e2e
         run-tenant-multi-e2e.ps1 + run-ip-demo-e2e.ps1
```

**One-click gate** (recommended before local release):

```powershell
$env:JAVA_HOME = "D:\IT\JAVA\JAVA17"
cd D:\WORK\Project\zestflow

# 1) Default services (single mode) running :8080 / :20550 / :20650
powershell -File .\scripts\blackbox\run-enterprise-gate.ps1

# 2) Full enterprise profile (after Admin restart)
# mvn spring-boot:run -pl zestflow-admin -Dspring-boot.run.profiles=local,enterprise-e2e
powershell -File .\scripts\blackbox\run-enterprise-gate.ps1 -RequireEnterpriseProfile
```

---

## 3. Execution Chain Coverage Matrix

| Stage | Coverage | Script/test |
|-------|----------|-------------|
| Login / JWT / RBAC | Layer B | full-e2e auth + security |
| Admin module CRUD lists | Layer B | full-e2e admin probes |
| Playground → Netty → chain execution | Layer B | 38 playground execute |
| Business API forwarding | Layer B | handleApplyAfterSale |
| Event collection / log query | Layer B | collector health + logs query |
| Executor registration heartbeat | Logs + unit | RegistryServiceImplTest |
| CONTINUE failure strategy | Layer B | SCN20260602000001 |
| Multi-tenant row isolation | Layer C | SCN20260602000002 tenant B only |
| IP → tenant mapping | Layer C | X-Forwarded-For 10.0.0.101/102 |
| Schedule trigger | Layer B | full-e2e + **idempotency key** `schedule-{id}-cron-{fireMs}` |
| Admin cluster ShedLock | Unit + cluster build | OfflineMonitor / TenantCleanup / ChainSync all tasks |
| Collector 8 routes | Unit | CollectorServerHandlerTest (32) |

**Still recommended manual or next-iteration automation:** None (Layer B includes playground-off probe script).

**E2E covered (Layer B):** Chain **publish + active-codes** (`run-chain-publish-e2e.ps1`), **full lifecycle create→publish→execute** (`run-chain-lifecycle-e2e.ps1`), **RBAC horizontal boundary** (`run-rbac-horizontal-e2e.ps1`), schedule trigger, Actuator health (HTTP probe), deploy-mode/cache probes, dashboard runtime topology (requires `npm run build` to sync static).

**Layer C — security-e2e Profile** (Admin + Executor + Collector restart): `registry-token` / `executor-access-token` / `collector access-token` 401 pairwise verification (`run-security-token-e2e.ps1`; gate `-RequireSecurityProfile`).

**Layer C — playground-disabled-e2e:** `run-playground-disabled-e2e.ps1`; gate `-RequirePlaygroundDisabledProfile`.

---

## 4. Optional E2E Profiles

### 4.1 enterprise-e2e / demo (Multi-tenant + IP Trial)

Files:

- `zestflow-admin/src/main/resources/application-enterprise-e2e.yml` (E2E gate)
- `zestflow-admin/src/main/resources/application-demo.example.yml` (public trial example, copy to `application-demo.yml`)

```yaml
zestflow:
  tenant:
    mode: multi
    ip-demo-mode: enabled
```

Start Admin E2E: `profiles=local,enterprise-e2e` → `run-enterprise-gate.ps1 -RequireEnterpriseProfile`

Public trial: `profiles=demo` (**do not combine with prod**). First visit by IP auto-calls unified `TenantProvisioner` to create trial tenant and clone master data.

**Database init/seed** (only two scripts):

```powershell
powershell -File scripts/init.ps1      # DDL: admin + executor + collector
powershell -File scripts/initData.ps1  # DML: seed data
```

Existing DB: drop and recreate recommended before re-running above (pre-release); `init.sql` includes all V3–V5 structural changes.

Public signup (optional): `POST /api/public/tenants/provision` (requires `public-provision-enabled: true`).

### 4.2 security-e2e (Machine Auth Pairwise)

Files:

- `zestflow-admin/src/main/resources/application-security-e2e.yml`
- `zestflow-demo/src/main/resources/application-security-e2e.yml`

```yaml
# Admin
zestflow.admin.registry-token: e2e-security-registry-token
zestflow.admin.executor-access-token: e2e-security-executor-token
zestflow.collector.access-token: e2e-security-collector-token

# Executor (must match)
zestflow.executor.access-token: e2e-security-executor-token
zestflow.executor.registry-token: e2e-security-registry-token

# Collector JDBC
zestflow.collector.access-token: e2e-security-collector-token
```

Start (**Admin + Executor + Collector all must restart**):

```powershell
# Admin
mvn spring-boot:run -pl zestflow-admin -Dspring-boot.run.profiles=local,security-e2e

# Executor
mvn spring-boot:run -pl zestflow-demo -Dspring-boot.run.profiles=local,security-e2e

# Collector JDBC
mvn spring-boot:run -pl zestflow-collector/collector-jdbc -Dspring-boot.run.profiles=local,security-e2e

# Verify
.\scripts\blackbox\run-security-token-e2e.ps1
.\scripts\blackbox\run-enterprise-gate.ps1 -SkipMavenTest -RequireSecurityProfile
```

---

## 5. enterprise-e2e / IP Trial Seed Notes

Seed data (`initData.sql`):

- Tenant B `id=2`, admin bound via `user_tenant`
- `tenant_ip_mapping`: `10.0.0.101→2`, `10.0.0.102→1` (E2E pre-seeded; new IPs auto-create trial tenant via `TenantProvisioner`)
- Scene `SCN20260602000002` only `tenant_id=2`

**Note:** IP isolation requires `mode=multi` (MyBatis-Plus tenant row plugin); ip-demo alone with single mode is ineffective. Business unique constraints are all `(tenant_id, …)`, see `init.sql`.

---

## 6. Security and Reliability

| Item | Default dev | Production recommendation |
|------|-------------|---------------------------|
| Playground | JWT + RBAC required | **`enabled=false`** (prod guard enforced) |
| Registry | Empty token = allow + WARN | **`registry-token` required** (prod guard enforced) |
| Executor Netty | access-token optional | **Pair with Admin** (prod guard enforced) |
| Collector | access-token optional | **Pair with Admin** (prod guard enforced) |
| JWT | application.yml dev value | **≥32 char random string** (prod guard enforced) |
| Default admin | admin/admin123 | **Strong password + forced change on first login** (prod forbids admin123) |
| IP demo | enterprise-e2e only | **Forbidden** in production enabled (prod guard enforced) |

Performance: Layer B 75-step chain ~75ms locally; load test via `run-blackbox.ps1` QPS segment (not required gate pass).

---

## 7. Pre-Release Checklist

- [ ] GitHub **CI** (Layer A) all green: `.github/workflows/ci.yml` (check Actions after first push)
- [x] `run-enterprise-gate.ps1` Layer A+B all PASS (local, 2026-06-02)
- [x] `scripts/deploy/verify-prod-templates.ps1` — prod templates no admin123 / playground enabled
- [x] **prod startup guards** — Admin / Executor / Collector `*ProductionGuard` (tokens, JWT, playground off)
- [x] Seed: `scripts/init.ps1` + `scripts/initData.ps1` (tenant B + IP mapping + full master clone seed)
- [x] Admin `enterprise-e2e` + `-RequireEnterpriseProfile` all PASS (multi isolation + IP demo)
- [x] Admin cluster ShedLock: schedule / offline detect / tenant cleanup / chain sync cache (`-Pcluster` + `deploy-mode=cluster`)
- [x] Admin→Executor idempotency key end-to-end (schedule cron/manual, Playground)
- [x] Embedded mode default single-layer async (starter `zestflow-starter-defaults.properties`)
- [ ] Admin + Executor `security-e2e` + `-RequireSecurityProfile` all PASS (registry / executor token)
- [x] After frontend changes `cd zestflow-admin-ui && npm run build` (output to admin static)
- [x] `mvn package` full reactor `-DskipTests` passes
- [x] README / gate docs point to `RELEASE_READINESS.md`
- [ ] Maven Central first release: `mvn clean deploy -Prelease -DskipTests` (see §8 below)

---

## 8. Maven Central First Release (cn.zestflow.www)

**Published artifacts (9):** `zestflow`, `zestflow-common`, `zestflow-executor`, `zestflow-starter`, `zestflow-collector`, `collector-core`, `collector-jdbc`, `collector-kafka`, `collector-rabbitmq`

**Not published:** `zestflow-admin`, `zestflow-demo` (`maven-deploy-plugin skip=true`)

### Completed (Code Side)

- [x] Version `0.1.0`, `distributionManagement`, `release` profile
- [x] developer / issueManagement metadata (`zestcc@126.com`)
- [x] Release scripts: [`scripts/maven/verify-release.ps1`](../scripts/maven/verify-release.ps1), [`scripts/maven/publish-central.ps1`](../scripts/maven/publish-central.ps1)
- [x] settings template: [`maven/settings.xml.example`](../maven/settings.xml.example)

### Pending (Private Key + Token)

| Step | Action |
|------|--------|
| 1 | Install [Gpg4win](https://www.gpg4win.org/download.html) |
| 2 | Export private key from original machine: `gpg --export-secret-keys 5B28B71AF1128C97 > zestflow-secret.asc` |
| 3 | Import on this machine: `gpg --import zestflow-secret.asc` |
| 4 | Verify: `gpg --list-secret-keys --keyid-format LONG` (should show `sec ... 5B28B71AF1128C97`) |
| 5 | Copy `maven/settings.xml.example` → `%USERPROFILE%\.m2\settings.xml`, fill **Central Portal User Token** + **GPG passphrase** |
| 6 | Run official release (below) |

**Central Portal User Token (not legacy OSSRH password):**

- Entry: https://central.sonatype.com/usertoken
- Or: Account → Generate User Token
- In `settings.xml` `<server><id>central</id>` (**not** `ossrh`)
- Token shown once only—save securely

**GPG public key (uploaded to keyserver):**

- UID: `zestflow <zestcc@126.com>`
- Key ID: `5B28B71AF1128C97`
- Fingerprint: `3C3D03110B28D04E5C92B6075B28B71AF1128C97`

### Commands

```powershell
# Requires JDK 17 — can verify release artifacts first (no GPG / Token)
powershell -File scripts/maven/verify-release.ps1

# After private key + settings.xml ready — one-click publish
powershell -File scripts/maven/publish-central.ps1
```

Equivalent manual commands:

```powershell
$env:JAVA_HOME = "D:\IT\JAVA\JAVA17"   # Adjust for your machine

# Verify
mvn clean verify -Prelease -DskipTests "-Dgpg.skip=true"

# Publish (do not add gpg.skip)
mvn clean deploy -Prelease -DskipTests
```

After upload, confirm status **Published** at [Central Portal → Deployments](https://central.sonatype.com/publishing/deployments) (`autoPublish=true` usually completes automatically). Index visible in minutes to ~2 hours.

> **Note:** OSSRH (`oss.sonatype.org`) was retired June 2025; this project uses `central-publishing-maven-plugin` + Central Portal User Token.

---

## CI (GitHub Actions)

| Workflow | Trigger | Content |
|----------|---------|---------|
| [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | push / PR → `main`/`master`/`develop` | Layer A five-module `mvn test` + full `package -DskipTests` |
| [`.github/workflows/e2e-manual.yml`](../.github/workflows/e2e-manual.yml) | Manual | Note: E2E requires local/self-hosted Runner running `run-enterprise-gate.ps1` |

Badge (available after repo push):

```markdown
![CI](https://github.com/zestcc/zestflow/actions/workflows/ci.yml/badge.svg)
```

---

## 9. Related Documentation

- [FULL_E2E_TEST_REPORT.en.md](./FULL_E2E_TEST_REPORT.en.md)
- [BLACKBOX_TEST_REPORT.en.md](./BLACKBOX_TEST_REPORT.en.md)
- [ARCHITECTURE.en.md](./ARCHITECTURE.en.md)
- [scripts/blackbox/README.md](../scripts/blackbox/README.md)
