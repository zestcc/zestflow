# Changelog

> **Language** English · [简体中文](CHANGELOG.md)

## [1.0.0-SNAPSHOT] - 2026-06-13

### Added

- **StrictV1 gate**: `scripts/blackbox/run-v1-acceptance.ps1` (full `mvn test` + cluster + npm build + all-profile E2E + strict production-acceptance)
- **WebSocket** log execution trace (enabled by default; frontend `streamExecutionTraceAuto` prefers WS, falls back to SSE)
- Node fallback `config.fallback.mode`: `default` / `constant` / `propagate` (when no fallback component is set)
- v1.0 docs: [V1_0_ROADMAP.md](docs/V1_0_ROADMAP.md), [MIGRATION_0.x_to_1.0.en.md](docs/MIGRATION_0.x_to_1.0.en.md), [MULTI_TENANT.md](docs/guides/MULTI_TENANT.md)
- SSO/OIDC enterprise login (Authorization Code + PKCE)
- [STRICT_V1_ACCEPTANCE.md](docs/guides/STRICT_V1_ACCEPTANCE.md) — full 8080-stack checklist

### Changed

- All Maven modules unified at **1.0.0-SNAPSHOT**
- `zestflow.admin.log-live-stream.websocket-enabled` defaults to **true**
- `/system/features` exposes `logLiveStream.websocketEnabled`
- [ARCHITECTURE.en.md](docs/ARCHITECTURE.en.md) §8.5 documents **1.0 API stability** freeze

## [0.1.0] - 2026-06-03

### Added

- Public deployment guide [`docs/DEPLOY.en.md`](docs/DEPLOY.en.md) and production configuration templates
- Admin / Executor / Collector production startup guards (`ProductionSecretGuard`)
- Playground `playground_record` MEDIUMTEXT migration and response body soft truncation
- Chain publish `chainData` → `graphData` fallback
- Full E2E coverage (32 feature probes + 38 Playground scenarios)

### Changed

- All modules unified at version **0.1.0**
- Actuator health probes and Admin default port 8080
