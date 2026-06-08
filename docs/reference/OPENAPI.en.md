# OpenAPI Usage Guide

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** Reference · **Language** English · [简体中文](OPENAPI.md)

ZestFlow Admin REST API provides a machine-readable **OpenAPI 3.0** specification, complementing the human-maintained [API.en.md](API.en.md).

---

## 1. Specification Sources

| Source | Path | Coverage |
|--------|------|----------|
| **Auto-generated (springdoc)** | `/v3/api-docs` | Admin `@RestController` (prefix `/api/zestflow`) |
| **Static snapshot** | [openapi/admin-api.json](../openapi/admin-api.json) | Same as above; for CI / offline reference |
| **Manually maintained** | [API.en.md](API.en.md) | Executor / Collector Netty, auth details, design conventions |

---

## 2. Local Swagger UI

1. Configure `application-local.yml` (see [GETTING_STARTED.md](../GETTING_STARTED.en.md))
2. Ensure `springdoc.swagger-ui.enabled: true` (`application-local.example.yml` enables it by default)
3. Start Admin: `mvn spring-boot:run -pl zestflow-admin -Dspring-boot.run.profiles=local`
4. Open in browser: **http://localhost:8080/swagger-ui.html**

In the UI, click **Authorize** (top right) and enter JWT: `Bearer <token>` (obtain via `/api/zestflow/auth/login`).

---

## 3. Export Static JSON

While Admin is running:

```powershell
powershell -File scripts/docs/export-openapi.ps1
# Optional: -BaseUrl http://127.0.0.1:8080 -Output docs/openapi/admin-api.json
```

```bash
bash scripts/docs/export-openapi.sh
```

---

## 4. Authentication Schemes (OpenAPI Components)

| Scheme ID | Type | Purpose |
|-----------|------|---------|
| `bearer-jwt` | HTTP Bearer | Most Admin user APIs |
| `registry-token` | API Key `X-Registry-Token` | `/registry/**` machine registration |

Some endpoints (e.g. `POST /chains/sync`) are permitAll at the Security layer but may still require registry-token filter validation.

---

## 5. Production Environment Policy

Under the `prod` profile, `AdminProductionGuard` **enforces**:

```yaml
springdoc:
  api-docs.enabled: false
  swagger-ui.enabled: false
```

Do not expose API documentation endpoints on the public internet. Export specification JSON from a development environment and publish with the repository.

---

## 6. Division of Labor with API.en.md

| Scenario | Recommended |
|----------|-------------|
| Look up Admin endpoint paths, parameters, DTO fields | OpenAPI / Swagger UI |
| Understand `/execute` Netty contract | [API.en.md](API.en.md) §2 |
| Understand Collector query API | [API.en.md](API.en.md) §5 |
| Common questions | [FAQ.en.md](FAQ.en.md) |

---

## 7. Release Checklist

- [ ] Run `export-openapi.ps1` after starting Admin locally
- [ ] Commit `docs/openapi/admin-api.json` diff (if Controllers changed)
- [ ] Confirm `application-prod.example.yml` disables springdoc

---

## Related Documentation

- Spec file: `docs/openapi/admin-api.json`
- Live docs (local only): `/swagger-ui.html`
- Manual supplement for Netty APIs: [API.en.md](API.en.md)
- [CONFIGURATION.en.md](CONFIGURATION.en.md) — Admin configuration
