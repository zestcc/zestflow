# ZestFlow

![CI](https://github.com/zestcc/zestflow/actions/workflows/ci.yml/badge.svg)

一个轻量级的业务流程编排器，将系统中复杂的方法编排成可复用的执行节点。

> **v0.1.0** — 开发环境默认 `admin/admin123`、机器令牌为空；**公网部署必须使用 `prod` profile**，见 [公网部署指南](docs/DEPLOY.md)。

## 快速引入

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Maven Central：[cn.zestflow.www](https://central.sonatype.com/namespace/cn.zestflow.www)

发布验证：`powershell -File scripts/maven/verify-release.ps1`（需 JDK 17）  
正式发布：`powershell -File scripts/maven/publish-central.ps1`（需 GPG 私钥 + Sonatype Token，见 [docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md) §8）

## 质量门禁

- **CI（GitHub Actions）**：push/PR 自动跑 Layer A 单元测试 + 全模块编译，见 [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
- **本地发布前**：`powershell -File scripts/blackbox/run-enterprise-gate.ps1`（含 38 场景黑盒 E2E）
- **公网部署前**：`powershell -File scripts/deploy/verify-prod-templates.ps1` + [docs/DEPLOY.md](docs/DEPLOY.md)
- 完整说明：[docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md)

## 公网部署（prod profile）

1. 复制 `application-prod.example.yml` → `application-prod.yml`（Admin / 业务应用）
2. 替换全部 `change-me-*` 占位符（JWT、三台机器令牌、管理员口令）
3. 仅暴露 Admin **8080**（建议 Nginx TLS）；20550 / 20650 / 8081 保持内网
4. 启动：`--spring.profiles.active=prod` — 启动守卫会自动拒绝弱配置

详见 **[docs/DEPLOY.md](docs/DEPLOY.md)**。
