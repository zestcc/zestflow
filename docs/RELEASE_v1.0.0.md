# ZestFlow v1.0.0 发布说明

> **日期** 2026-06-14 · **tag** `v1.0.0` · [CHANGELOG](../CHANGELOG.md)

## 概要

ZestFlow **1.0.0** 是首个正式 API 稳定版本：嵌入式业务流程编排 + 全链路可观测 + Cron 调度 + Admin 可视化建链，面向 Spring Boot 团队生产试点。

## 亮点

- **StrictV1 门禁全绿**：`mvn test`、Admin cluster 构建、`npm run build`、全 profile E2E、perf + offline 生产验收
- **WebSocket 日志流 GA**：默认开启，前端 WS 优先、SSE 回退
- **企业能力**：SSO/OIDC（PKCE）、多租户 How-to、RBAC 横向 E2E
- **API 稳定承诺**：自 1.0 起公共 REST/协议冻结范围见 [ARCHITECTURE.md](ARCHITECTURE.md) §8.5

## Maven 依赖

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 升级

从 0.x 升级请参阅 [MIGRATION_0.x_to_1.0.md](MIGRATION_0.x_to_1.0.md)。

## 验收复现

```powershell
.\scripts\blackbox\run-v1-acceptance.ps1
```

详见 [guides/STRICT_V1_ACCEPTANCE.md](guides/STRICT_V1_ACCEPTANCE.md)。

## 后续

- Maven Central `1.0.0` 构件发布（`scripts/maven/publish-central.ps1`）
- Gitee / GitHub Release 二进制包（Admin 单 jar）
