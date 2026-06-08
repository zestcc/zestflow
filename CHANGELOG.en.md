# Changelog

> **Language** English · [简体中文](CHANGELOG.md)

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
