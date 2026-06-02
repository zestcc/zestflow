# ZestFlow

![CI](https://github.com/zestcc/zestflow/actions/workflows/ci.yml/badge.svg)

一个轻量级的业务流程编排器，将系统中复杂的方法编排成可复用的执行节点。

## 质量门禁

- **CI（GitHub Actions）**：push/PR 自动跑 Layer A 单元测试 + 全模块编译，见 [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
- **本地发布前**：`powershell -File scripts/blackbox/run-enterprise-gate.ps1`（含 38 场景黑盒 E2E）
- 完整说明：[docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md)
