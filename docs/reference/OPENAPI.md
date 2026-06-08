# OpenAPI 使用指南

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Reference · [← 文档中心](../README.md) · [English](OPENAPI.en.md)

ZestFlow Admin REST API 提供 **OpenAPI 3.0** 机器可读规范，与 [API.md](API.md) 人工文档互补。

---

## 1. 规范来源

| 来源 | 路径 | 覆盖范围 |
|------|------|---------|
| **自动生成（springdoc）** | `/v3/api-docs` | Admin `@RestController`（前缀 `/api/zestflow`） |
| **静态快照** | [openapi/admin-api.json](../openapi/admin-api.json) | 同上，用于 CI/离线查阅 |
| **人工维护** | [API.md](API.md) | Executor/Collector Netty、鉴权细节、设计约定 |

---

## 2. 本地 Swagger UI

1. 配置 `application-local.yml`（见 [GETTING_STARTED.md](../GETTING_STARTED.md)）
2. 确保 `springdoc.swagger-ui.enabled: true`（`application-local.example.yml` 已默认开启）
3. 启动 Admin：`mvn spring-boot:run -pl zestflow-admin -Dspring-boot.run.profiles=local`
4. 浏览器打开：**http://localhost:8080/swagger-ui.html**

在 UI 右上角 **Authorize** 填入 JWT：`Bearer <token>`（登录 `/api/zestflow/auth/login` 获取）。

---

## 3. 导出静态 JSON

Admin 运行中时：

```powershell
powershell -File scripts/docs/export-openapi.ps1
# 可选：-BaseUrl http://127.0.0.1:8080 -Output docs/openapi/admin-api.json
```

```bash
bash scripts/docs/export-openapi.sh
```

---

## 4. 鉴权方案（OpenAPI Components）

| Scheme ID | 类型 | 用途 |
|-----------|------|------|
| `bearer-jwt` | HTTP Bearer | 绝大多数 Admin 用户 API |
| `registry-token` | API Key `X-Registry-Token` | `/registry/**` 机器注册 |

部分端点（如 `POST /chains/sync`）在 Security 层 permitAll，但仍可能需要 registry-token 过滤器校验。

---

## 5. 生产环境策略

`prod` profile 下 `AdminProductionGuard` **强制**：

```yaml
springdoc:
  api-docs.enabled: false
  swagger-ui.enabled: false
```

公网不得暴露 API 文档端点。规范 JSON 从开发环境导出后随仓库发布。

---

## 6. 与 API.md 的分工

| 场景 | 推荐 |
|------|------|
| 查 Admin 端点路径、参数、DTO 字段 | OpenAPI / Swagger UI |
| 理解 `/execute` Netty 契约 | [API.md](API.md) §2 |
| 理解 Collector 查询 API | [API.md](API.md) §5 |
| 常见问题 | [FAQ.md](FAQ.md) |

---

## 7. 发版检查清单

- [ ] Admin 本地启动后运行 `export-openapi.ps1`
- [ ] 提交 `docs/openapi/admin-api.json` diff（若有 Controller 变更）
- [ ] 确认 `application-prod.example.yml` 关闭 springdoc

---

## Related (English)

- Spec file: `docs/openapi/admin-api.json`
- Live docs (local only): `/swagger-ui.html`
- Manual supplement for Netty APIs: [API.md](API.md)
