# ZestFlow Full End-to-End Test Report

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](FULL_E2E_TEST_REPORT.md) · **Type** Test report · [← Documentation hub](README.en.md)

| Item | Content |
|------|---------|
| Version | 0.1.0 |
| Test date | 2026-06-02 |
| Type | Black-box full flow + configuration matrix + multi-tenant probes |
| Automation scripts | `scripts/blackbox/run-full-e2e.ps1` (full Playground), `scripts/blackbox/run-blackbox.ps1` (quick smoke) |
| Detailed black-box report | [BLACKBOX_TEST_REPORT.en.md](./BLACKBOX_TEST_REPORT.en.md) |
| Latest result samples | Generated locally in `scripts/blackbox/results/` after run (not committed) |

---

## 1. Test Objectives

On **real running processes** (Admin + zestflow-demo + Collector), verify:

1. **Main path:** Login → admin module APIs → Executor Netty health/business API → Collector health → log query  
2. **Playground:** 38 scenarios batch `execute` (Admin → Executor Netty → chain or MVC forward, **no direct Tomcat 8081**)  
3. **Multi-tenancy:** Tenant list, switch tenant, tenant CRUD lists (API should work even in single-tenant mode)  
4. **Security configuration matrix:** Missing/forged JWT, Registry Token dev allow, etc. (**restart required** switches in §4)  
5. **Config on/off:** Runtime probes + documented restart verification steps  

---

## 2. Environment and Startup

```text
Script/browser ──JWT──▶ Admin :8080
                    ├──▶ Executor Netty :20550 (chain /api/* forward)
                    └──▶ Collector :20650 (logs)

zestflow-demo:
  Tomcat 127.0.0.1:8081 (localhost only, not exposed to Admin)
  Netty 0.0.0.0:20550 (sole external execution channel)
```

| Item | Requirement |
|------|-------------|
| `JAVA_HOME` | `D:\IT\JAVA\JAVA17` (do not point to `...\JDK` subdirectory) |
| Admin | `mvn spring-boot:run -pl zestflow-admin` |
| Executor test app | `mvn spring-boot:run -pl zestflow-demo` |
| MySQL | `application-local.yml` (do not commit passwords) |

### One-Click Full Flow

```powershell
$env:JAVA_HOME = "D:\IT\JAVA\JAVA17"
cd D:\WORK\Project\zestflow
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\blackbox\run-full-e2e.ps1
# Including 50-step long chain and other slow scenarios:
powershell -File .\scripts\blackbox\run-full-e2e.ps1 -E2eProfile fullGreen -SceneTimeoutSec 300
```

Results written to `scripts/blackbox/results/full-e2e-{timestamp}.json`.

---

## 3. Automated Results Summary (2026-06-02)

### 3.1 Functional Probes (`run-full-e2e.ps1`)

| Category | Cases | Result | Notes |
|----------|-------|--------|-------|
| auth | Login / userinfo | Pass | admin / admin123 |
| config | `/api/system/features` (**JWT required**) | Pass | `playground.enabled=true` |
| tenant | My tenants / tenant list / switch-tenant/1 | Pass | Single-tenant default `tenant_id=1` |
| admin | dashboard, executors, collectors, chains, designs, components, schedules, users, roles, dict, logs | Pass | List GETs |
| executor | Netty `/health`, business API | Pass | `handleApplyAfterSale` + applyId |
| collector | `GET /collector/health` | Pass | :20650 (not `/health`) |
| playground | list-all (32 scenarios) | Pass | |
| playground | Batch execute (SkipHeavy) | **28/32 pass** (2026-06-02 16:21) | 4 chain scenarios HTTP 500 (design placeholder nodes unbound); after-sale API path validation fixed |
| blackbox smoke | `run-blackbox.ps1` | **15/17** | Rate limit 35 consecutive hits no 429 (RateLimiter TBD); wrong password is body.code≠200 |
| security | Forged JWT → `/api/dashboard/stats` | Pass | HTTP 403 |
| security | No JWT access dashboard | Pass | HTTP **403** |
| security | Registry no Token | Pass | Dev env empty token allows |

### 3.2 Playground Scenario Matrix

- Seed data: `zestflow-admin/src/main/resources/db/initData.sql` (32+ scenarios)  
- Path convention: `/execute` or `/api/**` only; legacy DB storing `http://localhost:8081/api/...` auto-normalized by `PlaygroundServiceImpl.normalizeRequestPath` to relative path  
- Success criteria: HTTP 200 and `Result.data.status == 1` (business execution success)  

Full 38-scenario classification in [BLACKBOX_TEST_REPORT.en.md §5](./BLACKBOX_TEST_REPORT.en.md).

### 3.3 Unit Tests (Compile-Time)

```bash
mvn test -pl zestflow-admin,zestflow-executor -am
```

Includes `NettyMvcDispatcherTest`, `PlaygroundRequestPathValidatorTest`, `ExecutorProxyServiceTest`, Playground-related Controller/Service tests.

---

## 4. Configuration On/Off Matrix (Restart Required)

The following switches **cannot hot-toggle in the same process**; change config, restart corresponding service, then run `run-full-e2e.ps1` or manual cases.

| Config | Default (dev) | ON expected | OFF expected | Verification |
|--------|---------------|-------------|--------------|--------------|
| `zestflow.playground.enabled` | `true` (local) | `/api/playground/**` 200 | Bean not loaded → **404** | features API + `GET .../scenes/list-all` |
| `zestflow.mail.enabled` | Often `false` | `SmtpMailService` sends | `NoopMailService` logs only | Register/forgot/create user (check logs) |
| `zestflow.admin.registry-token` | Empty | Wrong Token → **401** | Empty=dev allow | `POST /api/registry/register` |
| `zestflow.admin.executor-access-token` | Empty | Admin proxy sends `X-Access-Token` | Must match executor | Playground execute / chain proxy |
| `zestflow.executor.access-token` | Empty | Netty validates Token | Empty=allow | Direct Netty POST |
| `zestflow.tenant.mode` | `single` | `multi` enables MP tenant row plugin | No `tenant_id` filter | Restart, create second tenant + data isolation |
| `zestflow.tenant.ip-demo-mode` | `disabled` | `enabled` → `TenantIpFilter` maps tenant by IP | Filter disabled | Different IP → check `tenant_id` |
| `zestflow.admin.deploy-mode` | `standalone` | cluster enables Redis runtime state | Standalone default memory, **no Redis** | Multiple Admin replicas → cluster + `spring.data.redis.*` |
| `zestflow.admin.cache.type` | `caffeine` | `redis` enables RedisCacheManager | Standalone default Caffeine | Independent of deploy-mode; multi-replica suggest redis |

### 4.1 Playground Disabled Example

`application-local.yml` or environment variable:

```yaml
zestflow:
  playground:
    enabled: false
```

After Admin restart:

```powershell
# Should 404
Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/playground/scenes/list-all" -Headers @{Authorization="Bearer <token>"}
```

### 4.2 Multi-Tenant (multi) Example

```yaml
zestflow:
  tenant:
    mode: multi
```

After Admin restart, manual verification recommended:

1. `GET /api/tenants` create tenant B  
2. User bound only to tenant B → after login `GET /api/chains` must not see tenant A data  
3. After `POST /api/auth/switch-tenant/{id}` lists change with tenant  

Current automation covers **API reachability only** (`switch-tenant/1` returns 200 in single-tenant mode).

### 4.3 Registry / Executor Token Pairwise Enable

Admin `application-local.yml`:

```yaml
zestflow:
  admin:
    registry-token: "e2e-registry-secret"
    executor-access-token: "e2e-exec-secret"
```

zestflow-demo:

```yaml
zestflow:
  executor:
    access-token: "e2e-exec-secret"
```

After restart: wrong `X-Access-Token` / Registry header should **401**; Playground and chain proxy need correct Token.

### 4.4 E2E Scenario Policy (fullGreen / partialGreen / skipOnError)

Policy file: `scripts/blackbox/e2e-scene-policy.json`, loaded by `run-full-e2e.ps1`.

| Profile | Purpose | Command example |
|---------|---------|-----------------|
| **fullGreen** (default) | All required scenarios must pass; **does not** skip 75-step stress chain by default | `.\scripts\blackbox\run-full-e2e.ps1 -E2eProfile fullGreen -SceneTimeoutSec 300` |
| **partialGreen** | Also covers `SCN20260602000001`: chain `CHN_DEMO_CONTINUE_ON_ERROR` (`errorStrategy=CONTINUE`, node `failStep` fails but chain succeeds) | `-E2eProfile partialGreen` |
| **skipOnError** | `optionalScenes` failures recorded as skipped, exit may still be 0; profile defaults `-SkipHeavyScenes` | `-E2eProfile skipOnError` |

Report JSON fields: `e2ePolicy`, `sceneSummary.requiredPass/Fail`, `optionalSkipped`, `heavySkipped`. Required failure → **exit 1**.

Unrelated to schedule "route strategy"; chain-level failure strategy see engine `errorStrategy` (STOP / CONTINUE).

---

## 5. Multi-Tenant API List

| API | Auth | Single-tenant | multi mode additional behavior |
|-----|------|---------------|-------------------------------|
| `GET /api/auth/tenants` | JWT | Returns user's accessible tenants | Same |
| `POST /api/auth/switch-tenant/{id}` | JWT | Switches context | Affects subsequent queries' `tenant_id` |
| `GET /api/tenants` | JWT + permission | Super admin sees all | Create/edit tenants |
| Business table CRUD | JWT + `tenant_id` column | Default `tenant_id=1` | MyBatis-Plus tenant plugin auto-appends condition |

`tenant_id` / `app_code` written by `TenantAppContext` + `MetaObjectHandler` / Service, see `CLAUDE.md` data audit policy.

---

## 6. Security and Architecture Conclusions (E2E Related)

| Item | Conclusion |
|------|------------|
| Playground → business | Only `ExecutorProxyService` → Netty, **no** Admin direct `host:8081` |
| `/api/auth/**` | `permitAll`; JWT tests must use protected paths like `/api/dashboard/**` |
| No JWT | Measured **403** (not 401) |
| Endpoint list | No Tomcat URL |
| Legacy absolute `request_path` URL | Execution layer normalized to `/api/...` |

---

## 7. Known Issues and Follow-up

| ID | Issue | Status |
|----|-------|--------|
| E2E-01 | Seed scenario `SCN20260601000229` default empty `applyId` causes 400 | Script `Prepare-SceneBody` patched; recommend updating `initData.sql` default body |
| E2E-02 | Config off (playground off, token on, multi) needs **restart** for automation | Documented (§4); may add `application-e2e-off.yml` profile |
| E2E-03 | Multi-tenant **data isolation** | `run-tenant-multi-e2e.ps1` + profile `enterprise-e2e` |
| E2E-06 | IP demo isolation | `run-ip-demo-e2e.ps1` + same profile; see [RELEASE_READINESS.en.md](./RELEASE_READINESS.en.md) |
| E2E-04 | Heavy scenario `SCN20260531000004` (75 steps) | **fullGreen** does not SkipHeavy by default; smoke uses `-E2eProfile skipOnError` |
| E2E-05 | Three-tier scenario policy | `e2e-scene-policy.json` + §4.4; `partialGreen` scenario `SCN20260602000001` |

---

## 8. Reproduction Checklist

- [ ] Admin :8080, Netty :20550, Collector :20650 processes alive  
- [ ] `mvn test -pl zestflow-admin,zestflow-executor -am` passes  
- [ ] `run-full-e2e.ps1` Functional 32/32 all green, Playground 38/38 (fullGreen)  
- [ ] `run-blackbox.ps1` smoke + optional QPS  
- [ ] (Optional) playground.enabled=false restart verify 404  
- [ ] (Optional) registry-token + executor access-token pairwise verify 401  
- [ ] (Optional) tenant.mode=multi + dual-tenant isolation  

---

*Report updates with `scripts/blackbox/results/*.json`; on major architecture changes sync [ARCHITECTURE.en.md](./ARCHITECTURE.en.md) and [BLACKBOX_TEST_REPORT.en.md](./BLACKBOX_TEST_REPORT.en.md).*
