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

- Maven Central `1.0.0`：`scripts/maven/verify-release.ps1` 已通过 → 密钥就绪后 `scripts/maven/publish-central.ps1`
- Gitee Release 包：
  ```powershell
  mvn install -pl zestflow-admin -am -DskipTests
  cd zestflow-admin-ui; npm run build; cd ..
  mvn package -pl zestflow-admin -DskipTests
  powershell -File scripts/deploy/package-admin.ps1 -SkipBuild
  $env:GITEE_TOKEN = "<私人令牌>"
  powershell -File scripts/deploy/publish-gitee-release.ps1
  ```

## 下载（Release 附件）

| 平台 | 文件 |
|------|------|
| Linux | [zestflow_admin_1.0.0_linux.tar.gz](https://gitee.com/zestcc/zestflow/releases/download/v1.0.0/zestflow_admin_1.0.0_linux.tar.gz) |
| Windows | [zestflow_admin_1.0.0_win.zip](https://gitee.com/zestcc/zestflow/releases/download/v1.0.0/zestflow_admin_1.0.0_win.zip) |
