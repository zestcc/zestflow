# 多租户模式

> **类型**：How-to · [← 文档中心](../README.md)

## 模式说明

| 配置 | 值 | 说明 |
|------|-----|------|
| `zestflow.admin.tenant.mode` | `single`（默认） | 单租户，所有数据 `tenant_id=1` |
| `zestflow.admin.tenant.mode` | `multi` | JWT 携带租户，API 按租户隔离 |

## 启用多租户

1. Admin `application-local.yml` 或 prod：

```yaml
zestflow:
  admin:
    tenant:
      mode: multi
```

2. 重启 Admin。

3. 在 **设置 → 租户管理** 创建租户；顶栏下拉切换租户（会刷新 JWT）。

4. 验证：

```powershell
mvn spring-boot:run -pl zestflow-admin "-Dspring-boot.run.profiles=local,enterprise-e2e"
.\scripts\blackbox\run-tenant-multi-e2e.ps1
```

## IP 演示隔离（可选）

```yaml
zestflow:
  admin:
    tenant:
      ip-demo-mode: enabled
```

配合 `run-ip-demo-e2e.ps1`（`enterprise-e2e` profile）。

## 前端

- 请求头自动带 `X-Tenant-Id`（来自 `localStorage.currentTenantId`）
- 登录响应写入 `tenantStore`，顶栏 `AppHeader` 可切换

## 限制（1.0）

- 无租户自助注册 / 计费
- Executor 业务库租户字段由业务侧 `tenant_id` 列 + 应用配置维护
