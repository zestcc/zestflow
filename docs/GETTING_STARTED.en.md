# Getting Started

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Estimated time** 30 minutes · **Language** English · [简体中文](GETTING_STARTED.md)

This tutorial walks you through running ZestFlow locally from scratch and executing your first chain. When you finish, you will understand how Admin, Executor, and Collector work together.

---

## Prerequisites

| Dependency | Version |
|------------|---------|
| JDK | 17+ |
| Maven | 3.8+ |
| MySQL | 8.x |
| Node.js (frontend changes only) | 18 or 20 LTS |

---

## 1. Prepare databases

Create three databases (names must match configuration):

```sql
CREATE DATABASE IF NOT EXISTS zestflow_admin DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS zestflow_app_bussiness DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS zestflow_app_log DEFAULT CHARSET utf8mb4;
```

**Admin database:** Flyway runs `db/migration/V*.sql` automatically when Admin starts.

**Business / log databases:** Initialized on first Demo startup via Flyway / init scripts (see resource directories under `zestflow-executor` and `collector-jdbc`).

---

## 2. Configure local data sources

```bash
# Admin
cp zestflow-admin/src/main/resources/application-local.example.yml \
   zestflow-admin/src/main/resources/application-local.yml

# Demo (Executor + Collector)
cp zestflow-demo/src/main/resources/application-local.example.yml \
   zestflow-demo/src/main/resources/application-local.yml
```

Edit both `application-local.yml` files and replace `your-db-username` / `your-db-password` with your MySQL credentials.

---

## 3. Build and start

```bash
# Install dependency modules (first time or after pulling code)
mvn install -pl zestflow-demo -am -DskipTests

# Terminal 1: Admin (port 8080)
mvn spring-boot:run -pl zestflow-admin -Dspring-boot.run.profiles=local

# Terminal 2: Demo business app (port 8081, includes Executor + Collector)
mvn spring-boot:run -pl zestflow-demo -Dspring-boot.run.profiles=local
```

**Expected results:**

- Admin logs show successful Flyway migration
- Demo logs show `Executor register success` and Collector registration
- Browser: http://localhost:8080 — default credentials `admin` / `admin123` (local development only)
- (Optional) OpenAPI docs: http://localhost:8080/swagger-ui.html (springdoc UI enabled under local profile)

---

## 4. Verify three-tier connectivity

After logging into Admin, check:

| Menu location | Expected |
|---------------|----------|
| **Executor management** | `demo-app` module appears, status online |
| **Component management** | Demo `@ZestComponent` components scanned |
| **Collector management** | Collector instance online |

Default ports:

| Component | Port | Description |
|-----------|------|-------------|
| Admin | 8080 | UI + REST API |
| Demo Tomcat | 8081 | Business HTTP |
| Executor Netty | 20550 | Admin callback for chain execution |
| Collector Netty | 20650 | Admin event queries |

---

## 5. Execute your first chain (Playground)

1. Go to **Playground → Scene list**
2. Select any built-in scene (e.g. after-sales processing)
3. Click **Execute** and review the structured response and node details
4. Go to **Log query**, filter by chain code, and click trace to view the execution graph

This validates the full path: **Admin dispatch → Executor Netty execution → Collector persistence → Admin query & display**.

---

## 6. Write your first component (optional)

Add the dependency to your business project:

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
@ZestComponent("hello")
public class HelloHandler {

    @ZestExecute(value = "greet", name = "Greet")
    public Map<String, Object> greet(@ZestParam("name") String name) {
        return Map.of("message", "Hello, " + name);
    }
}
```

Minimum configuration:

```yaml
spring.application.name: my-app
zestflow.executor.admin-addresses: http://localhost:8080
zestflow.executor.port: 20550
```

After restarting the app, Admin **Component management** should list `hello.greet`. Drag it onto the canvas in the designer to build a chain. See the [Component Development Guide](guides/COMPONENT_DEVELOPMENT.en.md) for details.

---

## 7. Frontend development (optional)

```bash
cd zestflow-admin-ui
pnpm install
pnpm dev    # http://localhost:8001, API proxied to 8080
```

After frontend changes, build and embed into Admin:

```bash
pnpm build   # Output to zestflow-admin/src/main/resources/static/
```

---

## FAQ

**Demo registration fails / executor offline**

- Confirm Admin is running and `zestflow.executor.admin-addresses` points to `http://localhost:8080`
- Check firewall rules for ports 20550 / 20650 (usually not an issue locally)

**Log page shows no data**

- Confirm Collector is online; Admin-side `zestflow.collector.api-url` defaults to `http://localhost:20650`
- Run Playground first, then query logs

**Flyway startup error**

- For development databases, see rebaseline instructions in [FLYWAY_POLICY.md](FLYWAY_POLICY.en.md)

---

## Next steps

| Goal | Document |
|------|----------|
| Deep dive into architecture | [ARCHITECTURE.md](ARCHITECTURE.en.md) |
| Write complex components | [guides/COMPONENT_DEVELOPMENT.en.md](guides/COMPONENT_DEVELOPMENT.en.md) |
| Visual chain building & publish | [guides/CHAIN_ORCHESTRATION.en.md](guides/CHAIN_ORCHESTRATION.en.md) |
| Production deployment | [DEPLOY.en.md](DEPLOY.en.md) |
| Configuration reference | [reference/CONFIGURATION.md](reference/CONFIGURATION.en.md) |
