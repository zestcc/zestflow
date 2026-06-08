# Documentation Maintenance Policy

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](DOCUMENTATION_MAINTENANCE.md)

This document defines version control, update triggers, and quality checklists for ZestFlow documentation to keep docs and code aligned over time.

---

## 1. Documentation architecture

Uses **Diátaxis** four categories:

| Type | Location | Update frequency |
|------|----------|------------------|
| Tutorial | `GETTING_STARTED.md`, `MCP_SETUP.md` | Low (when flows are stable) |
| How-to | `guides/`, `DEPLOY.md` | Medium |
| Explanation | `ARCHITECTURE.md`, `adr/`, `PROJECT_SUMMARY.md` | Medium |
| Reference | `QUICK_REFERENCE.md`, `reference/` | **High** (follows API/config changes) |

Entry index: [docs/README.en.md](README.en.md) (English) · [docs/README.md](README.md) (Chinese)

---

## 2. Version identifiers

Every user-facing document header must include:

```markdown
> **Version** 0.1.0 · **Updated** YYYY-MM-DD · **Type** Tutorial|How-to|...
```

English documents add:

```markdown
> **Language** English · [简体中文](xxx.md)
```

Chinese documents add:

```markdown
> ... · [English](xxx.en.md)
```

- **Version:** aligned with root `pom.xml` `<version>` (batch update on release)
- **Updated:** date of last substantive change

### 2.1 Bilingual maintenance rules

| Rule | Description |
|------|-------------|
| Naming | English mirror in the same directory: `API.md` ↔ `API.en.md` |
| Sync | When editing Chinese user docs, update the matching `.en.md` in the **same PR** (or open a follow-up Issue) |
| Cross-links | Chinese header `[English](xxx.en.md)`; English header `[简体中文](xxx.md)` |
| Index | New docs must update `CATALOG.md`, `CATALOG.en.md`, `README.md`, `README.en.md` |
| Tool | Run `scripts/docs/add-bilingual-headers.ps1` to batch-add missing links |
| Verify | Run `scripts/docs/verify-bilingual-docs.ps1` before release or in CI |
| EN links | Run `scripts/docs/fix-en-internal-links.ps1` after bulk EN edits |
| ZH cross-links | Run `scripts/docs/fix-zh-crosslinks.ps1` if `[简体中文]` headers break |

---

## 3. Code changes that trigger doc updates

| Code change | Required documentation updates |
|-------------|------------------------------|
| Configuration property change | `reference/CONFIGURATION.md`, `*Properties.java`, corresponding `application.yml` |
| New/modified Admin REST endpoint | `reference/API.md` (Netty supplements), run `export-openapi.ps1` to update `openapi/admin-api.json` |
| New annotation / engine API | `reference/ANNOTATIONS.md`, `reference/EXECUTION_ENGINE.md`, `QUICK_REFERENCE.md` |
| New SPI | `reference/SPI.md`, `ARCHITECTURE.md` §12 |
| Frequent user questions | `reference/FAQ.md` |
| New Flyway version | `FLYWAY_POLICY.md`, migration README |
| Port / deployment flow change | `DEPLOY.md`, `GETTING_STARTED.md`, `reference/GLOSSARY.md` |
| Release | `CHANGELOG.md`, README version badges, document header versions |
| Architecture decision | New `docs/adr/*.md` |

When updating Chinese docs, mirror changes in corresponding `*.en.md` files where they exist.

---

## 4. PR documentation checklist

Contributors self-check in PRs (Reviewers verify):

- [ ] Does the code behavior change have corresponding documentation?
- [ ] Do configuration property names match `*Properties.java`?
- [ ] Are example commands understandable on Windows / Linux (or platform noted)?
- [ ] Are new terms added to `reference/GLOSSARY.md` / `GLOSSARY.en.md`?
- [ ] No duplicated large sections (cross-link instead of copy)?
- [ ] Do frontend changes note that `pnpm build` is required?
- [ ] Are English mirrors updated when Chinese user docs change?
- [ ] Does `scripts/docs/verify-bilingual-docs.ps1` pass?

---

## 5. Release documentation workflow

1. Update `CHANGELOG.md` (Added / Changed / Fixed)
2. Batch-update document header **Version** numbers
3. Verify `README.md` / `README.en.md` feature lists and screenshots
4. Run [RELEASE_READINESS.md](RELEASE_READINESS.en.md) checklist
5. Sync Git tag with Maven release

---

## 6. Accuracy verification

| Dimension | Verification method |
|-----------|---------------------|
| Configuration defaults | Compare `application.yml` + `@ConfigurationProperties` |
| Ports | Compare `ExecutorProperties`, `CollectorRegistryProperties` |
| Annotations | Compare `zestflow-executor` annotation package |
| API paths | Compare `*Controller.java` |
| Module list | Compare root `pom.xml` `<modules>` |

Recommend a documentation audit each minor version (reuse this checklist).

---

## 7. Known gaps

| Item | Status | Notes |
|------|--------|-------|
| OpenAPI/Swagger export | ✅ | `springdoc` + `scripts/docs/export-openapi.ps1` + `docs/openapi/` |
| English documentation parity | In progress | Core hub, guides, deploy, contributing translated 2026-06-08 |
| Video tutorials | None | Optional community contribution |

---

## 8. Documentation quality scoring (10-point scale)

| Score | Standard |
|-------|----------|
| 9–10 | Full-path coverage; zero conflicts with code; clear Diátaxis; new users running in 30 minutes |
| 7–8 | Core paths complete; minor stale config or missing How-to |
| 5–6 | README usable; missing Reference or deployment details |
| <5 | Scattered docs; obvious inconsistency with code |

**Current target: 9+** (after 2026-06-08 documentation refactor)

---

## Related links

- [docs/README.en.md](README.en.md) — Documentation hub (English)
- [docs/README.md](README.en.md) — Documentation hub (Chinese)
- [CONTRIBUTING.en.md](../CONTRIBUTING.en.md) — Contribution process
