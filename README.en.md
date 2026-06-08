<p align="center">
  <img src="docs/assets/logo-readme.svg" width="200" alt="ZestFlow"/>
</p>

<p align="center">
  <a href="README.md">简体中文</a> ·
  <a href="https://github.com/zestcc/zestflow">GitHub</a> ·
  <a href="https://gitee.com/zestcc/zestflow">Gitee</a> ·
  <a href="https://www.zestflow.cn">Website</a>
</p>

<p align="center">
  <a href="https://github.com/zestcc/zestflow/actions/workflows/ci.yml"><img src="https://github.com/zestcc/zestflow/actions/workflows/ci.yml/badge.svg" alt="CI"/></a>
  <img src="https://img.shields.io/badge/Java-17+-orange" alt="Java 17+"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/version-0.1.0-blue" alt="version"/>
  <img src="https://img.shields.io/badge/license-Apache%202.0-green" alt="license"/>
</p>

<p align="center">If this project helps you, a Star is appreciated.</p>

---

## Overview

ZestFlow is a business process orchestration engine for Spring Boot. It breaks Service method calls into reusable execution nodes and automatically records each node's inputs, outputs, latency, and errors.

Unlike LiteFlow (rule orchestration) or xxl-job (task scheduling), ZestFlow combines **orchestration + visual design + tracing + Cron scheduling** in one embeddable package — no BPMN required.

Personal open-source project, currently **v0.1.0**, published on [Maven Central](https://central.sonatype.com/namespace/cn.zestflow.www).

## Design editor

<p align="center">
  <img src="docs/assets/design-editor.png" alt="ZestFlow design editor — after-sales flow" width="920"/>
</p>

<p align="center"><sub>Built-in AntV X6 editor: drag nodes from the palette; supports decision, selector, script, sub-chain, and more.</sub></p>

## Documentation

**[📖 Documentation hub (English)](docs/README.en.md)** · [简体中文文档中心](docs/README.en.md) — full index (Tutorial / How-to / Reference). All **35** articles are available in Chinese and English.

| Scenario | Document |
|----------|----------|
| Get running in 30 min | [Getting started](docs/GETTING_STARTED.en.md) |
| Full catalog (bilingual) | [CATALOG.en.md](docs/CATALOG.en.md) |
| Architecture | [ARCHITECTURE.en.md](docs/ARCHITECTURE.en.md) |
| Components & chains | [Component guide](docs/guides/COMPONENT_DEVELOPMENT.en.md) · [Chain orchestration](docs/guides/CHAIN_ORCHESTRATION.en.md) |
| Configuration | [Configuration reference](docs/reference/CONFIGURATION.en.md) · [API](docs/reference/API.en.md) · [OpenAPI](docs/reference/OPENAPI.en.md) |
| Production deploy | [DEPLOY.en.md](docs/DEPLOY.en.md) |
| AI integration | [AI_COPILOT.en.md](docs/AI_COPILOT.en.md) · [MCP_SETUP.en.md](docs/MCP_SETUP.en.md) |
| Contributing | [CONTRIBUTING.en.md](CONTRIBUTING.en.md) |
| Changelog | [CHANGELOG.en.md](CHANGELOG.en.md) |

## Features

* **Method-level components:** Each `@ZestExecute` method in a `@ZestComponent` class is a node — finer than class-level components.
* **Visual chain builder:** AntV X6 editor in Admin; drag-and-drop, no hard-coded DAG.
* **Tracing:** Per-node events; replay execution by traceId in Admin.
* **Async collection:** Collector persists events without blocking business threads; JDBC / Kafka / RabbitMQ.
* **Hot reload:** Chain definition changes reload on Executor without restart.
* **Cron scheduling:** Multi-executor registry, routing, and failover.
* **Lightweight embed:** Add `zestflow-starter` to your project.
* **Playground:** 32+ demo scenarios to validate chains quickly.

## When to use

Good fit:

- Services growing too long; if-else chains hard to maintain
- Want visual orchestration with per-node execution records
- Existing Spring Boot app; don't want full Camunda

Not a fit:

- In-memory rules only → LiteFlow
- Approval / human tasks → Flowable / Camunda
- Cron-only, no DAG → xxl-job

## Architecture

```
Admin (:8080)          register / schedule / log query
    │
    ▼
Business app (Spring Boot)
    ├── Executor (:20550)   run DAG
    └── Collector (:20650)  collect events → MySQL
```

Admin does not own your chain data — it governs and proxies. Chain definitions, designs, and events live in three MySQL schemas on the business side. See [ARCHITECTURE.md](docs/ARCHITECTURE.en.md).

## Quick start

**Dependency:**

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**Component:**

```java
@ZestComponent("order")
public class OrderHandler {

    @ZestExecute(value = "createOrder", name = "Create order")
    public OrderCreatedResult createOrder(
            @ZestParam("userId") String userId,
            @ZestParam("amount") double amount) {
        return new OrderCreatedResult("ORD-" + System.currentTimeMillis(), amount);
    }
}
```

**Config:**

```yaml
spring.application.name: my-shop
zestflow.executor.admin-addresses: http://localhost:8080
zestflow.executor.port: 20550
# See application-local.example.yml for datasources
```

On startup the Executor registers with Admin; scan components, design chains, and run from the UI.

## Run locally

```bash
# 1. Run db/init.sql + initData.sql
# 2. Copy application-local.example.yml → application-local.yml
# 3. Start
mvn install -pl zestflow-demo -am -DskipTests
mvn spring-boot:run -pl zestflow-admin
mvn spring-boot:run -pl zestflow-demo
```

- Admin: http://localhost:8080, login `admin` / `admin123` (local only)
- Frontend: `cd zestflow-admin-ui && pnpm dev`, run `pnpm build` after changes

## vs LiteFlow / xxl-job

| | LiteFlow | xxl-job | ZestFlow |
|---|:---:|:---:|:---:|
| Core | Rule orchestration | Task scheduling | Orchestration + trace + schedule |
| Visual UI | No | Task mgmt | DAG designer |
| Per-node trace | Weak | No | Yes |
| Embed | jar | Separate center | Starter |

## FAQ

**Production ready?**  
v0.1.0 — fine for personal projects and small-team trials. Prod guards and E2E exist, but the community is early; canary critical paths first.

**Admin down?**  
Loaded chains still run on Executor; scheduling and log queries are affected. Admin cluster HA not done yet.

## Contributing

Issues and PRs welcome. Fork → change → `mvn test -pl zestflow-admin,zestflow-executor -am` → PR.

[GitHub Issues](https://github.com/zestcc/zestflow/issues) · [Gitee Issues](https://gitee.com/zestcc/zestflow/issues)

## License

[Apache License 2.0](LICENSE)
