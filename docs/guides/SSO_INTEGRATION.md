# SSO 集成指南

ZestFlow Admin 通过 **可插拔 SSO SPI** 对接身份提供方，默认集成 [ZestSSO](https://github.com/zest/sso)（OIDC + PKCE）。本地 JWT 与 RBAC 不变：SSO 仅负责身份联邦，登录成功后仍签发 ZestFlow JWT。

## 架构

```
前端 LoginPage
  │ GET /auth/sso/config      → 是否展示 SSO 按钮
  │ GET /auth/sso/authorize   → PKCE 授权 URL
  ▼
IdP（ZestSSO / 通用 OIDC）
  │ redirect ?code&state
  ▼
/login/callback → POST /auth/sso/callback
  │ code + state 换 id_token → JWKS 校验 → 本地用户映射
  ▼
ZestFlow JWT + Pinia 登录态

退出：localStorage.sso_login=true → GET /auth/sso/logout-url → IdP SLO
```

### 后端 SPI

| 组件 | 职责 |
|------|------|
| `SsoAuthService` | 门面，委托 Registry |
| `SsoProviderRegistry` | 按 `enabled` + `provider` 路由 |
| `ZestSsoProvider` | ZestSSO（Discovery + logout-url API） |
| `GenericOidcSsoProvider` | Keycloak / Authing 等标准 OIDC |
| `DisabledSsoProvider` | `enabled=false` 或 `provider=none` |
| `AbstractOidcSsoProvider` | PKCE 授权 / Token 交换 / 回调 |
| `SsoPkceStore` | standalone 内存 / cluster Redis |

扩展新 IdP：实现 `SsoProvider` 或继承 `AbstractOidcSsoProvider`，注册为 Spring Bean 即可。

## 配置

前缀：`zestflow.sso.*`（完整属性见 [CONFIGURATION.md](../reference/CONFIGURATION.md)）

### 开发（ZestSSO @ localhost:9000）

```yaml
zestflow:
  sso:
    enabled: true
    provider: zest-sso
    issuer: http://localhost:9000
    discovery-uri: http://localhost:9000/api/public/.well-known/openid-configuration
    client-id: zestflow-admin
    client-secret: change-me-in-production
    redirect-uri: http://localhost:5173/login/callback
    post-logout-redirect-uri: http://localhost:5173/login
    scopes: openid,profile,email,roles,tenant
    claims:
      admin-role: SSO_ADMIN
    zest-sso:
      use-logout-url-api: true
```

Vite 开发：`redirect-uri` 指向前端 `5173`；生产单 jar 部署时改为 Admin 对外域名 + `/login/callback`。

### 生产

- `spring.profiles.active=prod` 且 `sso.enabled=true` 时，`AdminProductionGuard` 强制：
  - `client-secret` 非 `change-me*` 占位符
  - `client-id`、`redirect-uri` 已配置
- 参考 `application-prod.example.yml`

### 切换通用 OIDC

```yaml
zestflow:
  sso:
    enabled: true
    provider: oidc
    display-name: Corporate SSO
    discovery-uri: https://idp.example.com/.well-known/openid-configuration
    client-id: zestflow-admin
    client-secret: <secret>
    redirect-uri: https://admin.example.com/login/callback
```

## ZestSSO 侧

预注册客户端 `zestflow-admin`（ZestSSO 内置）：

| 项 | 值 |
|----|-----|
| redirect_uri | 与 `zestflow.sso.redirect-uri` 精确一致 |
| scopes | openid, profile, email, roles, tenant |
| PKCE | 必须（S256） |

角色映射：`SSO_ADMIN` → `user.is_super_admin = 1`。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/sso/config` | 公开配置（enabled、provider、displayName） |
| GET | `/auth/sso/authorize` | 生成 PKCE 授权 URL |
| POST | `/auth/sso/callback` | `{ code, state }` → LoginVO |
| GET | `/auth/sso/logout-url` | RP-Initiated Logout URL |

## 数据库

Flyway `V6__sso_integration.sql`：

- `user.sso_subject` — IdP `sub`
- `user.sso_provider` — 提供方 ID（`zest-sso` / `oidc`）
- 唯一索引 `(sso_provider, sso_subject)`

## 联调清单

1. `curl http://localhost:9000/api/public/.well-known/openid-configuration`
2. 启用 `zestflow.sso.enabled=true`，重启 Admin
3. 登录页出现「ZestSSO 登录」按钮
4. 完成回调，检查 `user.sso_subject` 已写入
5. 退出时跳转 ZestSSO SLO 并回到登录页

## 相关文档

- ZestSSO：`D:/project/zest/zest-sso/docs/integration-guide.md`
- [CONFIGURATION.md](../reference/CONFIGURATION.md) — 配置项速查
- [DEPLOY.md](../DEPLOY.md) — 生产部署
