# 校验 Admin Flyway 迁移（发布前）
# 生成 V1 已冻结 — 表结构变更请新增 V2__*.sql
$ErrorActionPreference = 'Stop'
$validate = Join-Path $PSScriptRoot 'validate-admin-flyway.ps1'
& $validate
exit $LASTEXITCODE
