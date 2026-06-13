# Gitee Go 流水线说明
#
# 在 Gitee 仓库 → 流水线 → 新建流水线 → 选择「自定义 YAML」：
#   - Layer A：`ci.yml`（push/PR 触发 mvn test + package）
#   - Layer B～D：`nightly-e2e.yml`（定时或手动；需 Runner 预装 Admin + demo）
#
# GitHub Actions 等价物见 `.github/workflows/ci.yml` 与 `nightly-e2e.yml`。
