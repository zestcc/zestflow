# 常见问题（FAQ）

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Reference · [← 文档中心](../README.md) · [English](FAQ.en.md)

---

## 入门与部署

### Q1：Admin 启动后 Flyway 报错怎么办？

**A：** 开发库可参阅 [FLYWAY_POLICY.md](../FLYWAY_POLICY.md)。常见处理：

- `checksum mismatch` → 非 prod 环境启动时自动 `repair()`
- 旧 Beta history 漂移 → `FlywayLegacyHistoryCleaner` 或 `scripts/deploy/rebaseline-admin-dev.ps1`
- **生产环境禁止**自动清 history，须 DBA 评审

### Q2：Demo 注册失败 / 执行器显示离线？

**A：** 检查清单：

1. Admin 已启动（8080）
2. `zestflow.executor.admin-addresses=http://localhost:8080`
3. `registry-token` 与 Admin `zestflow.admin.registry-token` 一致（生产必填）
4. 端口 20550 / 20650 未被占用

### Q3：日志页无数据？

**A：**

1. Collector 在 Admin **采集器管理** 中显示在线
2. Admin 配置 `zestflow.collector.api-url`（默认 `http://localhost:20650`）
3. 先执行 Playground 或 `/execute` 产生事件后再查询
4. 确认 Demo 中 `zestflow.collector.registry.port=20650`

### Q4：修改前端后不生效？

**A：** 前端嵌入 Admin jar，须在 `zestflow-admin-ui/` 执行 `pnpm build`，产物输出到 `zestflow-admin/src/main/resources/static/`。

---

## 链执行

### Q5：链发布成功但执行报「链不存在」？

**A：**

1. 确认链已 **发布**（非仅保存设计）
2. 目标 Executor 在线且 reload 成功
3. `chainCode` 与请求一致；或用 `chainKey`（优先于 chainCode）
4. 检查 `appCode` 是否匹配

### Q6：Netty `/execute` 与 Tomcat `/execute` 有什么区别？

**A：**

| | Netty :20550 | Tomcat :8081 |
|---|-------------|--------------|
| 响应 | **固定**完整 `ChainExecuteResultDTO` | 受 `execute-response-mode` 影响 |
| 用途 | Admin 试验场、调度回调 | 业务 HTTP 集成 |
| 开关 | 始终可用 | `execute-endpoint-enabled=true` |

Admin 试验场**必须**走 Netty。详见 [EXECUTION_ENGINE.md](EXECUTION_ENGINE.md) §7。

### Q7：链失败但 HTTP 返回 200？

**A：** 这是**设计行为**。Netty `/execute` 通过 `ChainExecuteResultDTO.status`（`4`=成功，`5`=失败）判定，便于结构化解析。不要仅用 HTTP 状态码判断业务成败。

### Q8：如何编程式执行链？

**A：**

```java
@Autowired ChainExecutionEngine engine;

ChainExecuteResultDTO result = engine.execute("my-chain",
    Map.of("userId", "U001"),
    orderDto);
```

详见 [EXECUTION_ENGINE.md](EXECUTION_ENGINE.md)。

---

## 元件开发

### Q9：Admin 元件列表为空？

**A：**

1. 确认类有 `@ZestComponent` 且为 Spring Bean
2. 方法有 `@ZestExecute`
3. 应用已注册到 Admin
4. 调用 `POST /api/components/refresh` 或重启应用

### Q10：@ZestParam 取不到值？

**A：**

1. 确认 `params` 中 key 与 `@ZestParam.value` 一致
2. 检查上游节点 `outputData` / 参数绑定器配置
3. `required=true` 时缺失会抛异常；可设 `defaultValue`

### Q11：节点重试导致重复写库？

**A：** 元件方法应设计为**幂等**。节点配置 `ERROR_STRATEGY_RETRY` 时会多次调用。

---

## 调度

### Q12：Admin 挂了 Cron 还跑吗？

**A：** **会。** 业务 Cron 由 Executor `EmbeddedScheduleDriver` 读业务库 `zf_schedule` **自治执行**，不依赖 Admin 在线。Admin 停机影响：链发布、控制台改调度、查日志。详见 [adr/SCHEDULING.md](../adr/SCHEDULING.md)。

### Q13：多 Executor 实例 Cron 会重复触发吗？

**A：** 支持分片：`shardIndex` / `shardTotal` 过滤任务归属，避免重复。

---

## 安全与生产

### Q14：生产环境最低配置？

**A：**

- `--spring.profiles.active=prod`
- 更换 `jwt.secret`（≥32 字符）
- 配置三份独立 token：`registry-token`、`executor-access-token`、`collector.access-token`
- 仅 Admin :8080 经 TLS 公网暴露；20550/20650 内网
- 见 [DEPLOY.md](../DEPLOY.md)

### Q15：事件采集会影响业务性能吗？

**A：** 设计上**不应**。`AsyncEventPublisher` 使用有界队列 + 批量 drain + 熔断器；`offer()` 超时 ≤1ms。队列满时可磁盘降级（可选）。采集失败不阻断链执行。

---

## AI / MCP

### Q16：Copilot 和 Dev MCP 有什么区别？

**A：**

| | Admin Copilot | Dev MCP |
|---|--------------|---------|
| 场景 | 链编排、表达式、诊断 | IDE 内写 Java 元件 |
| 入口 | Admin UI | Cursor / Claude Desktop |
| 模块 | zestflow-admin | zestflow-mcp.jar |

详见 [AI_COPILOT.md](../AI_COPILOT.md)。

### Q17：MCP 连不上 Executor？

**A：** 启动 MCP 时须传 `--executor-url=http://localhost:20550`（Netty 端口，非 Tomcat 8081）。

---

## 文档与贡献

### Q18：文档与代码不一致怎么办？

**A：** 在 [Gitee Issues](https://gitee.com/zestcc/zestflow/issues) 标注 `documentation`，或参考 [DOCUMENTATION_MAINTENANCE.md](../DOCUMENTATION_MAINTENANCE.md) 提交 PR。API/注解以 `docs/reference/` 为准，发版时同步更新。

### Q19：如何查看 Admin OpenAPI 规范？

**A：** 本地 Swagger UI：http://localhost:8080/swagger-ui.html；导出：`scripts/docs/export-openapi.ps1`。详见 [OPENAPI.md](OPENAPI.md)。

---

## 相关文档

- [GETTING_STARTED.md](../GETTING_STARTED.md) — 30 分钟上手
- [API.md](API.md) — 接口参考
- [CONFIGURATION.md](CONFIGURATION.md) — 配置参考
- [GLOSSARY.md](GLOSSARY.md) — 术语表
