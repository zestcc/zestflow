# OpenAPI 规范目录

> **版本** 0.1.0 · **更新** 2026-06-08 · [English](README.en.md)

本目录存放从 Admin 运行时自动导出的 OpenAPI 3 JSON 快照，与 `zestflow-admin` Controller 源码同步。

| 文件 | 说明 |
|------|------|
| `admin-api.json` | Admin REST API 全量规范（`/api/zestflow/**`）；**由脚本生成，发版前提交** |

**注意：** Executor Netty（`:20550`）与 Collector Netty（`:20650`）不在 springdoc 扫描范围内，见 [API.md](../reference/API.md) 手工维护章节。

---

## 重新生成

**前置：** Admin 已启动（local profile，默认 `http://localhost:8080`）

```powershell
powershell -File scripts/docs/export-openapi.ps1
```

```bash
bash scripts/docs/export-openapi.sh
```

产物写入本目录 `admin-api.json`。发版前建议执行并提交 diff。

---

## 在线浏览（仅本地）

复制 `application-local.example.yml` → `application-local.yml` 后启动 Admin：

- Swagger UI：http://localhost:8080/swagger-ui.html
- JSON：http://localhost:8080/v3/api-docs

生产环境（`prod` profile）**强制关闭** OpenAPI 与 Swagger UI。

---

## 相关文档

- [OPENAPI.md](../reference/OPENAPI.md) — 使用说明
- [API.md](../reference/API.md) — 人工维护的补充说明（Netty、鉴权、注意事项）
