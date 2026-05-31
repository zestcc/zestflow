package com.zestflow.executor.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.event.PublishEventDTO;
import com.zestflow.common.model.ComponentType;
import com.zestflow.executor.scanner.ComponentScanner.ComponentMeta;
import com.zestflow.executor.chain.ChainLoader;
import com.zestflow.executor.chain.ChainPO;
import com.zestflow.executor.chain.ChainRepository;
import com.zestflow.executor.design.DesignPO;
import com.zestflow.executor.design.DesignRepository;
import com.zestflow.executor.engine.ChainExecutionEngine;
import com.zestflow.executor.event.EventPublisher;
import com.zestflow.executor.scanner.ComponentScanner;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Setter
@ChannelHandler.Sharable
public class ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChainExecutionEngine chainExecutionEngine;
    private final ChainRepository chainRepo;
    private final DesignRepository designRepo;
    private final ComponentScanner componentScanner;
    private final ChainLoader chainLoader;

    private EventPublisher eventPublisher;

    public ServerHandler(ChainExecutionEngine chainExecutionEngine, ChainRepository chainRepo, DesignRepository designRepo) {
        this(chainExecutionEngine, chainRepo, designRepo, null, null);
    }

    public ServerHandler(ChainExecutionEngine chainExecutionEngine, ChainRepository chainRepo, DesignRepository designRepo,
                         ComponentScanner componentScanner, ChainLoader chainLoader) {
        this.chainExecutionEngine = chainExecutionEngine;
        this.chainRepo = chainRepo;
        this.designRepo = designRepo;
        this.componentScanner = componentScanner;
        this.chainLoader = chainLoader;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (request.method() != HttpMethod.POST && request.method() != HttpMethod.GET
                && request.method() != HttpMethod.PUT && request.method() != HttpMethod.DELETE) {
            writeResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "{\"code\":405,\"message\":\"Method not allowed\"}");
            return;
        }

        String uri = request.uri();
        String content = request.content().toString(CharsetUtil.UTF_8);
        log.info("收到请求 method={} uri={}", request.method(), uri);

        try {
            if (!dispatchApiRoute(ctx, request.method(), uri, content)) {
                writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                        "{\"code\":404,\"message\":\"Not found: " + uri + "\"}");
            }
        } catch (Exception e) {
            log.error("请求处理失败 uri={}", uri, e);
            writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"code\":500,\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * API 路由分发
     *
     * @return true 表示已处理
     */
    private boolean dispatchApiRoute(ChannelHandlerContext ctx, HttpMethod method, String uri, String body) throws Exception {
        // 健康检查
        if ("/health".equals(uri) && method == HttpMethod.GET) {
            writeResponse(ctx, HttpResponseStatus.OK,
                    "{\"status\":\"UP\",\"timestamp\":" + System.currentTimeMillis() + "}");
            return true;
        }

        // 链执行
        if ("/execute".equals(uri) && method == HttpMethod.POST) {
            handleExecute(ctx, body);
            return true;
        }

        // 元件列表
        if (uri.startsWith("/api/components") && method == HttpMethod.GET) {
            handleListComponents(ctx, uri);
            return true;
        }

        // 元件刷新（运行时重新扫描 @ZestComponent）
        if ("/api/components/refresh".equals(uri) && method == HttpMethod.POST) {
            handleRefreshComponents(ctx);
            return true;
        }

        // 元件动态注册
        if ("/api/components/register".equals(uri) && method == HttpMethod.POST) {
            handleRegisterComponent(ctx, body);
            return true;
        }

        // 链 CRUD
        if (uri.startsWith("/api/chains")) {
            return dispatchChainRoutes(ctx, method, uri, body);
        }

        // 设计 CRUD
        if (uri.startsWith("/api/designs")) {
            return dispatchDesignRoutes(ctx, method, uri, body);
        }

        return false;
    }

    // ==================== 链路由 ====================

    private boolean dispatchChainRoutes(ChannelHandlerContext ctx, HttpMethod method, String uri, String body) throws Exception {
        String[] parts = uri.split("/");
        // /api/chains → ["", "api", "chains"]
        // /api/chains/{code} → ["", "api", "chains", "CODE"]
        // /api/chains/{code}/status → ["", "api", "chains", "CODE", "status"]

        if (parts.length == 3) {
            if (method == HttpMethod.GET) {
                return handleListChains(ctx, uri);
            }
            if (method == HttpMethod.POST) {
                return handleCreateChain(ctx, body);
            }
        }

        if (parts.length == 4 && "active-codes".equals(parts[3]) && method == HttpMethod.GET) {
            return handleActiveCodes(ctx);
        }

        if (parts.length == 4) {
            String code = stripQuery(parts[3]);
            if (method == HttpMethod.GET) {
                return handleGetChain(ctx, code);
            }
            if (method == HttpMethod.PUT) {
                return handleUpdateChain(ctx, code, body);
            }
            if (method == HttpMethod.DELETE) {
                return handleDeleteChain(ctx, code, uri, body);
            }
        }

        if (parts.length == 5 && "status".equals(parts[4]) && method == HttpMethod.PUT) {
            return handleToggleChainStatus(ctx, stripQuery(parts[3]), body);
        }

        // GET /api/chains/{code}/versions
        if (parts.length == 5 && "versions".equals(parts[4]) && method == HttpMethod.GET) {
            return handleListVersions(ctx, stripQuery(parts[3]));
        }

        if (parts.length == 5 && "reload".equals(parts[4]) && method == HttpMethod.PUT) {
            return handleReloadChain(ctx, stripQuery(parts[3]), body);
        }

        // POST /api/chains/{code}/rollback/{version}
        if (parts.length == 6 && "rollback".equals(parts[4]) && method == HttpMethod.POST) {
            return handleRollbackChain(ctx, stripQuery(parts[3]), stripQuery(parts[5]), body);
        }

        return false;
    }

    // ==================== 设计路由 ====================

    private boolean dispatchDesignRoutes(ChannelHandlerContext ctx, HttpMethod method, String uri, String body) throws Exception {
        String[] parts = uri.split("/");
        // /api/designs → ["", "api", "designs"]
        // /api/designs/{code} → ["", "api", "designs", "CODE"]
        // /api/designs/{code}/graph → ["", "api", "designs", "CODE", "graph"]
        // /api/designs/{code}/status → ["", "api", "designs", "CODE", "status"]
        // /api/designs/{code}/bindings → ["", "api", "designs", "CODE", "bindings"]
        // /api/designs/{code}/bindings/{chainCode} → ["", "api", "designs", "CODE", "bindings", "CHAINCODE"]

        if (parts.length == 3) {
            if (method == HttpMethod.GET) {
                return handleListDesigns(ctx, uri);
            }
            if (method == HttpMethod.POST) {
                return handleCreateDesign(ctx, body);
            }
        }

        if (parts.length == 4) {
            String code = stripQuery(parts[3]);
            if (method == HttpMethod.GET) {
                return handleGetDesign(ctx, code);
            }
            if (method == HttpMethod.PUT) {
                return handleUpdateDesign(ctx, code, body);
            }
            if (method == HttpMethod.DELETE) {
                return handleDeleteDesign(ctx, code, uri, body);
            }
        }

        if (parts.length == 5) {
            String code = stripQuery(parts[3]);
            String action = parts[4];
            if ("graph".equals(action) && method == HttpMethod.PUT) {
                return handleSaveDesignGraph(ctx, code, body);
            }
            if ("status".equals(action) && method == HttpMethod.PUT) {
                return handleToggleDesignStatus(ctx, code, body);
            }
            if ("bindings".equals(action) && method == HttpMethod.GET) {
                return handleGetBindings(ctx, code);
            }
            if ("bindings".equals(action) && method == HttpMethod.POST) {
                return handleBindChain(ctx, code, body);
            }
        }

        if (parts.length == 6 && "bindings".equals(parts[4]) && method == HttpMethod.DELETE) {
            return handleUnbindChain(ctx, stripQuery(parts[3]), stripQuery(parts[5]), uri, body);
        }

        return false;
    }

    // ==================== 链处理 ====================

    private boolean handleListChains(ChannelHandlerContext ctx, String uri) throws Exception {
        Map<String, String> params = parseQueryParams(uri);
        List<ChainPO> all = chainRepo.list(params.get("keyword"), parseInteger(params.get("status")));
        int page = parseInteger(params.getOrDefault("page", "1"));
        int size = parseInteger(params.getOrDefault("size", "10"));
        int total = all.size();
        log.info("查询链列表 total={} page={} size={} keyword={}", total, page, size, params.get("keyword"));

        List<ChainPO> paged = all.stream()
                .skip((long) (page - 1) * size)
                .limit(size)
                .collect(Collectors.toList());

        // 包装为分页 JSON
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total", total);
        root.put("current", page);
        root.put("size", size);
        ArrayNode records = root.putArray("records");
        for (ChainPO c : paged) {
            records.add(chainToJson(c));
        }
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
    }

    private boolean handleGetChain(ChannelHandlerContext ctx, String code) throws Exception {
        ChainPO data = chainRepo.get(code);
        if (data == null) {
            log.warn("链不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"链不存在: " + code + "\"}");
            return true;
        }
        log.info("查询链详情 code={} name={}", code, data.getName());
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(chainToJson(data)));
        return true;
    }

    private boolean handleCreateChain(ChannelHandlerContext ctx, String body) throws Exception {
        JsonNode json = MAPPER.readTree(body);
        String name = json.has("name") ? json.get("name").asText("") : "";
        String description = json.has("description") ? json.get("description").asText(null) : null;
        String moduleCode = json.has("moduleCode") ? json.get("moduleCode").asText(null) : null;
        Integer status = json.has("status") && !json.get("status").isNull() ? json.get("status").asInt() : null;
        String updatedBy = json.has("updatedBy") ? json.get("updatedBy").asText(null) : null;

        ChainPO data = chainRepo.create(name, description, moduleCode, status, updatedBy);
        log.info("创建链 code={} name={} moduleCode={} updatedBy={}", data.getCode(), name, moduleCode, updatedBy);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(chainToJson(data)));
        return true;
    }

    private boolean handleUpdateChain(ChannelHandlerContext ctx, String code, String body) throws Exception {
        JsonNode json = MAPPER.readTree(body);
        String name = json.has("name") && !json.get("name").isNull() ? json.get("name").asText() : null;
        String description = json.has("description") && !json.get("description").isNull() ? json.get("description").asText() : null;
        Integer status = json.has("status") && !json.get("status").isNull() ? json.get("status").asInt() : null;
        String updatedBy = json.has("updatedBy") ? json.get("updatedBy").asText(null) : null;

        ChainPO data = chainRepo.update(code, name, description, status, updatedBy);
        if (data == null) {
            log.warn("更新链不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"链不存在: " + code + "\"}");
            return true;
        }
        log.info("更新链 code={} name={} status={} updatedBy={}", code, name, status, updatedBy);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(chainToJson(data)));
        return true;
    }

    private boolean handleDeleteChain(ChannelHandlerContext ctx, String code, String uri, String body) throws Exception {
        String updatedBy = extractUpdatedBy(body);
        if (updatedBy == null) {
            Map<String, String> params = parseQueryParams(uri);
            updatedBy = params.get("updatedBy");
        }
        ChainPO removed = chainRepo.delete(code, updatedBy);
        if (removed == null) {
            log.warn("删除链不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"链不存在: " + code + "\"}");
            return true;
        }
        log.info("删除链 code={} name={} updatedBy={}", code, removed.getName(), updatedBy);
        writeResponse(ctx, HttpResponseStatus.OK, "{\"code\":200,\"message\":\"删除成功\"}");
        return true;
    }

    private boolean handleToggleChainStatus(ChannelHandlerContext ctx, String code, String body) throws Exception {
        String updatedBy = extractUpdatedBy(body);
        ChainPO data = chainRepo.toggleStatus(code, updatedBy);
        if (data == null) {
            log.warn("切换链状态不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"链不存在: " + code + "\"}");
            return true;
        }
        log.info("切换链状态 code={} status={} updatedBy={}", code, data.getStatus(), updatedBy);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(chainToJson(data)));
        return true;
    }

    // ==================== 设计处理 ====================

    private boolean handleListDesigns(ChannelHandlerContext ctx, String uri) throws Exception {
        Map<String, String> params = parseQueryParams(uri);
        List<DesignPO> all = designRepo.list(params.get("keyword"), parseInteger(params.get("status")));
        int page = parseInteger(params.getOrDefault("page", "1"));
        int size = parseInteger(params.getOrDefault("size", "10"));
        int total = all.size();
        log.info("查询设计列表 total={} page={} size={} keyword={}", total, page, size, params.get("keyword"));

        List<DesignPO> paged = all.stream()
                .skip((long) (page - 1) * size)
                .limit(size)
                .collect(Collectors.toList());

        ObjectNode root = MAPPER.createObjectNode();
        root.put("total", total);
        root.put("current", page);
        root.put("size", size);
        ArrayNode records = root.putArray("records");
        for (DesignPO d : paged) {
            List<ChainPO> bindings = designRepo.getBindings(d.getCode());
            records.add(designToJson(d, bindings));
        }
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
    }

    private boolean handleGetDesign(ChannelHandlerContext ctx, String code) throws Exception {
        DesignPO data = designRepo.get(code);
        if (data == null) {
            log.warn("设计不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"设计不存在: " + code + "\"}");
            return true;
        }
        List<ChainPO> bindings = designRepo.getBindings(code);
        String boundChainCodes = bindings.stream().map(ChainPO::getCode).collect(Collectors.joining(", "));
        log.info("查询设计详情 code={} name={} bindings=[{}]", code, data.getName(), boundChainCodes);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(designToJson(data, bindings)));
        return true;
    }

    private boolean handleCreateDesign(ChannelHandlerContext ctx, String body) throws Exception {
        JsonNode json = MAPPER.readTree(body);
        String name = json.has("name") ? json.get("name").asText("") : "";
        String description = json.has("description") ? json.get("description").asText(null) : null;
        String designer = json.has("designer") ? json.get("designer").asText(null) : null;
        String moduleCode = json.has("moduleCode") ? json.get("moduleCode").asText(null) : null;
        String graphData = json.has("graphData") ? json.get("graphData").asText(null) : null;
        String chainData = json.has("chainData") ? json.get("chainData").asText(null) : null;
        String updatedBy = json.has("updatedBy") ? json.get("updatedBy").asText(null) : null;

        DesignPO data = designRepo.create(name, description, designer, moduleCode, graphData, chainData, updatedBy);
        log.info("创建设计 code={} name={} moduleCode={} designer={}", data.getCode(), name, moduleCode, designer);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(designToJson(data, null)));
        return true;
    }

    private boolean handleUpdateDesign(ChannelHandlerContext ctx, String code, String body) throws Exception {
        JsonNode json = MAPPER.readTree(body);
        String name = json.has("name") && !json.get("name").isNull() ? json.get("name").asText() : null;
        String description = json.has("description") && !json.get("description").isNull() ? json.get("description").asText() : null;
        String designer = json.has("designer") && !json.get("designer").isNull() ? json.get("designer").asText() : null;
        Integer status = json.has("status") && !json.get("status").isNull() ? json.get("status").asInt() : null;
        String updatedBy = json.has("updatedBy") ? json.get("updatedBy").asText(null) : null;

        DesignPO data = designRepo.update(code, name, description, designer, status, updatedBy);
        if (data == null) {
            log.warn("更新设计不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"设计不存在: " + code + "\"}");
            return true;
        }
        List<ChainPO> bindings = designRepo.getBindings(code);
        log.info("更新设计 code={} name={} status={}", code, name, status);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(designToJson(data, bindings)));
        return true;
    }

    private boolean handleSaveDesignGraph(ChannelHandlerContext ctx, String code, String body) throws Exception {
        JsonNode json = MAPPER.readTree(body);
        String graphData = json.has("graphData") ? json.get("graphData").asText() : "";
        String chainData = json.has("chainData") ? json.get("chainData").asText() : "";
        String updatedBy = json.has("updatedBy") ? json.get("updatedBy").asText(null) : null;
        DesignPO data = designRepo.saveGraph(code, graphData, chainData, updatedBy);
        if (data == null) {
            log.warn("保存图谱设计不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"设计不存在: " + code + "\"}");
            return true;
        }
        // 2026-05-31：重置已发布链的发布状态（设计修改后需重新发布）
        chainRepo.resetBoundChainStatus(code, updatedBy);
        log.info("保存设计图谱 code={} updatedBy={}", code, updatedBy);
        writeResponse(ctx, HttpResponseStatus.OK, "{\"code\":200,\"message\":\"保存成功\"}");
        return true;
    }

    private boolean handleDeleteDesign(ChannelHandlerContext ctx, String code, String uri, String body) throws Exception {
        String updatedBy = extractUpdatedBy(body);
        if (updatedBy == null) {
            Map<String, String> params = parseQueryParams(uri);
            updatedBy = params.get("updatedBy");
        }
        DesignPO removed = designRepo.delete(code, updatedBy);
        if (removed == null) {
            log.warn("删除设计不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"设计不存在: " + code + "\"}");
            return true;
        }
        log.info("删除设计 code={} name={} updatedBy={}", code, removed.getName(), updatedBy);
        writeResponse(ctx, HttpResponseStatus.OK, "{\"code\":200,\"message\":\"删除成功\"}");
        return true;
    }

    private boolean handleToggleDesignStatus(ChannelHandlerContext ctx, String code, String body) throws Exception {
        String updatedBy = extractUpdatedBy(body);
        DesignPO data = designRepo.toggleStatus(code, updatedBy);
        if (data == null) {
            log.warn("切换设计状态不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"设计不存在: " + code + "\"}");
            return true;
        }
        log.info("切换设计状态 code={} status={} updatedBy={}", code, data.getStatus(), updatedBy);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(designToJson(data, null)));
        return true;
    }

    // ==================== 绑定处理 ====================

    private boolean handleGetBindings(ChannelHandlerContext ctx, String designCode) throws Exception {
        List<ChainPO> bindings = designRepo.getBindings(designCode);
        log.info("查询设计绑定 designCode={} count={}", designCode, bindings.size());
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total", bindings.size());
        ArrayNode records = root.putArray("records");
        for (ChainPO c : bindings) {
            records.add(chainToJson(c));
        }
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
    }

    private boolean handleUnbindChain(ChannelHandlerContext ctx, String designCode, String chainCode,
                                       String uri, String body) throws Exception {
        String updatedBy = extractUpdatedBy(body);
        if (updatedBy == null) {
            Map<String, String> params = parseQueryParams(uri);
            updatedBy = params.get("updatedBy");
        }
        if (designRepo.unbind(designCode, chainCode, updatedBy)) {
            log.info("解绑设计-链 designCode={} chainCode={} updatedBy={}", designCode, chainCode, updatedBy);
            writeResponse(ctx, HttpResponseStatus.OK, "{\"code\":200,\"message\":\"解绑成功\"}");
        } else {
            log.warn("解绑失败：绑定关系不存在 designCode={} chainCode={}", designCode, chainCode);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"绑定关系不存在\"}");
        }
        return true;
    }

    private boolean handleBindChain(ChannelHandlerContext ctx, String designCode, String body) throws Exception {
        JsonNode json = MAPPER.readTree(body);
        String chainCode = json.has("chainCode") ? json.get("chainCode").asText() : "";
        String updatedBy = json.has("updatedBy") ? json.get("updatedBy").asText(null) : null;
        if (chainCode.isEmpty()) {
            log.warn("绑定链缺少 chainCode designCode={}", designCode);
            writeResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    "{\"code\":400,\"message\":\"缺少 chainCode\"}");
            return true;
        }
        if (designRepo.bind(designCode, chainCode, updatedBy)) {
            log.info("绑定设计-链 designCode={} chainCode={} updatedBy={}", designCode, chainCode, updatedBy);
            writeResponse(ctx, HttpResponseStatus.OK, "{\"code\":200,\"message\":\"绑定成功\"}");
        } else {
            log.warn("绑定失败：设计或链不存在 designCode={} chainCode={}", designCode, chainCode);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"设计或链不存在\"}");
        }
        return true;
    }

    // ==================== 热加载（事件驱动） ====================

    private boolean handleReloadChain(ChannelHandlerContext ctx, String code, String body) throws Exception {
        if (chainLoader == null) {
            log.warn("ChainLoader 未注入，无法热加载 code={}", code);
            writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"code\":500,\"message\":\"ChainLoader 不可用\"}");
            return true;
        }

        // 解析发布事件（兼容纯 graphData 和 PublishEventDTO）
        String graphData = null;
        String chainData = null;
        String publishId = null;
        if (body != null && !body.isEmpty()) {
            try {
                JsonNode json = MAPPER.readTree(body);
                if (json.has("publishId") && !json.get("publishId").isNull()) {
                    publishId = json.get("publishId").asText();
                }
                if (json.has("graphData") && !json.get("graphData").isNull()) {
                    graphData = json.get("graphData").asText();
                }
                if (json.has("chainData") && !json.get("chainData").isNull()) {
                    chainData = json.get("chainData").asText();
                }
            } catch (Exception e) {
                log.warn("解析请求体失败 code={}", code);
            }
        }

        ChainLoader.ChainReloadResult result = chainLoader.reloadChainLocal(code, graphData, chainData);
        log.info("链热加载结果 code={} success={} nodes={} publishId={} msg={}",
                code, result.isSuccess(), result.getNodeCount(), publishId, result.getErrorMessage());

        // 返回发布事件 DTO
        PublishEventDTO event = PublishEventDTO.builder()
                .publishId(publishId)
                .eventType(result.isSuccess()
                        ? com.zestflow.common.model.event.ChainEventType.PUBLISH_EXECUTOR_COMPLETED
                        : com.zestflow.common.model.event.ChainEventType.PUBLISH_EXECUTOR_FAILED)
                .chainCode(code)
                .success(result.isSuccess())
                .nodeCount(result.getNodeCount())
                .errorMessage(result.getErrorMessage())
                .timestamp(System.currentTimeMillis())
                .build();

        writeResponse(ctx, result.isSuccess() ? HttpResponseStatus.OK : HttpResponseStatus.INTERNAL_SERVER_ERROR,
                MAPPER.writeValueAsString(event));
        return true;
    }

    // ==================== 链定义查询 ====================

    /**
     * 获取所有已发布链的编码列表（供 Admin 查询跨模块链定义）
     */
    private boolean handleActiveCodes(ChannelHandlerContext ctx) throws Exception {
        List<ChainPO> allChains = chainRepo.list(null, null);
        List<String> activeCodes = allChains.stream()
                .filter(c -> c.getStatus() != null && c.getStatus() >= 3)
                .map(ChainPO::getCode)
                .collect(java.util.stream.Collectors.toList());
        log.info("查询活跃链编码 total={}", activeCodes.size());
        String json = MAPPER.writeValueAsString(activeCodes);
        writeResponse(ctx, HttpResponseStatus.OK, json);
        return true;
    }

    // ==================== 版本管理 ====================

    /**
     * 查询链的所有版本快照
     * GET /api/chains/{code}/versions
     */
    private boolean handleListVersions(ChannelHandlerContext ctx, String code) throws Exception {
        List<com.zestflow.executor.chain.ChainVersionPO> versions = chainRepo.listVersionSnapshots(code);
        log.info("查询链版本列表 code={} count={}", code, versions.size());
        ArrayNode arr = MAPPER.createArrayNode();
        for (com.zestflow.executor.chain.ChainVersionPO v : versions) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", v.getId());
            node.put("version", v.getVersion());
            node.put("designCode", v.getDesignCode() != null ? v.getDesignCode() : "");
            node.put("createdBy", v.getCreatedBy() != null ? v.getCreatedBy() : "");
            node.put("createdAt", v.getCreatedAt() != null ? v.getCreatedAt() : "");
            arr.add(node);
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total", versions.size());
        root.set("records", arr);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
    }

    // ==================== 版本回滚 ====================

    /**
     * 回滚到链的指定版本
     * POST /api/chains/{code}/rollback/{version}
     */
    private boolean handleRollbackChain(ChannelHandlerContext ctx, String code, String versionStr, String body) throws Exception {
        int targetVersion;
        try {
            targetVersion = Integer.parseInt(versionStr);
        } catch (NumberFormatException e) {
            writeResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    "{\"code\":400,\"message\":\"无效的版本号: " + versionStr + "\"}");
            return true;
        }

        String updatedBy = extractUpdatedBy(body);
        ChainPO rolledBack = chainRepo.rollbackToVersion(code, targetVersion, updatedBy);
        if (rolledBack == null) {
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"链或版本快照不存在 code=" + code + " version=" + targetVersion + "\"}");
            return true;
        }

        // 回滚后尝试热加载旧版本设计
        if (chainLoader != null && rolledBack.getDesignCode() != null && !rolledBack.getDesignCode().isEmpty()) {
            try {
                // 从设计表读取回滚后的 design 数据
                DesignPO design = designRepo.get(rolledBack.getDesignCode());
                if (design != null) {
                    chainLoader.reloadChainLocal(code, design.getGraphData(), design.getChainData());
                    log.info("回滚后热加载完成 code={} version={} designCode={}", code, targetVersion, rolledBack.getDesignCode());
                }
            } catch (Exception e) {
                log.warn("回滚后热加载失败 code={}", code, e);
            }
        }

        log.info("链回滚完成 code={} targetVersion={} updatedBy={}", code, targetVersion, updatedBy);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(chainToJson(rolledBack)));
        return true;
    }

    // ==================== 元件热管理 ====================

    /**
     * 运行时刷新元件注册表（重新扫描所有 @ZestComponent Bean）
     */
    private boolean handleRefreshComponents(ChannelHandlerContext ctx) throws Exception {
        if (componentScanner == null) {
            writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"code\":500,\"message\":\"ComponentScanner 不可用\"}");
            return true;
        }
        int count = componentScanner.refresh();
        log.info("元件注册表刷新完成 count={}", count);
        writeResponse(ctx, HttpResponseStatus.OK,
                "{\"code\":200,\"message\":\"刷新成功\",\"count\":" + count + "}");
        return true;
    }

    /**
     * 运行时注册单个元件（不依赖 Spring Bean）
     */
    private boolean handleRegisterComponent(ChannelHandlerContext ctx, String body) throws Exception {
        if (componentScanner == null) {
            writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"code\":500,\"message\":\"ComponentScanner 不可用\"}");
            return true;
        }
        JsonNode json = MAPPER.readTree(body);
        String executeId = json.has("executeId") ? json.get("executeId").asText() : "";
        if (executeId.isEmpty()) {
            writeResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    "{\"code\":400,\"message\":\"executeId 不能为空\"}");
            return true;
        }

        ComponentMeta meta = new ComponentMeta();
        meta.setExecuteId(executeId);
        meta.setGroupName(json.has("groupName") ? json.get("groupName").asText() : "");
        meta.setName(json.has("name") ? json.get("name").asText() : "");
        meta.setDescription(json.has("description") ? json.get("description").asText() : "");
        meta.setTimeout(json.has("timeout") ? json.get("timeout").asLong() : -1);
        meta.setAsync(json.has("async") && json.get("async").asBoolean());
        if (json.has("componentType")) {
            try {
                meta.setComponentType(ComponentType.valueOf(json.get("componentType").asText()));
            } catch (Exception e) {
                meta.setComponentType(ComponentType.EXECUTOR);
            }
        }

        boolean isNew = componentScanner.register(executeId, meta);
        log.info("动态注册元件 executeId={} type={} isNew={}", executeId, meta.getComponentType(), isNew);
        writeResponse(ctx, isNew ? HttpResponseStatus.CREATED : HttpResponseStatus.OK,
                "{\"code\":200,\"message\":\"注册成功\",\"isNew\":" + isNew + "}");
        return true;
    }

    // ==================== 元件列表 ====================

    private boolean handleListComponents(ChannelHandlerContext ctx, String uri) throws Exception {
        Map<String, String> params = parseQueryParams(uri);
        String keyword = params.get("keyword");
        String executorIdFilter = params.get("executorId");
        Integer statusFilter = parseInteger(params.get("status"));
        String componentTypeFilter = params.get("componentType");
        log.info("查询元件列表 keyword={} executorId={} status={} componentType={}",
                keyword, executorIdFilter, statusFilter, componentTypeFilter);

        List<Map<String, Object>> all = new ArrayList<>();

        if (componentScanner != null) {
            for (Map.Entry<String, ComponentScanner.ComponentMeta> entry : componentScanner.getRegistry().entrySet()) {
                ComponentScanner.ComponentMeta meta = entry.getValue();
                // keyword 过滤
                if (keyword != null && !keyword.isEmpty()) {
                    String kw = keyword.toLowerCase();
                    if (!meta.getExecuteId().toLowerCase().contains(kw)
                            && !meta.getName().toLowerCase().contains(kw)
                            && !meta.getGroupName().toLowerCase().contains(kw)) {
                        continue;
                    }
                }
                // status 过滤（所有已扫描元件默认 active=1）
                if (statusFilter != null && statusFilter != 1) {
                    continue;
                }
                // componentType 过滤
                if (componentTypeFilter != null && !componentTypeFilter.isEmpty()
                        && !meta.getComponentType().name().equals(componentTypeFilter)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("componentId", meta.getExecuteId());
                item.put("componentName", meta.getName() != null ? meta.getName() : "");
                item.put("description", meta.getDescription() != null ? meta.getDescription() : "");
                item.put("groupName", meta.getGroupName() != null ? meta.getGroupName() : "");
                item.put("timeout", meta.getTimeout());
                item.put("async", meta.isAsync());
                item.put("componentType", meta.getComponentType().name());
                item.put("tagDefs", meta.getTagDefs());
                item.put("status", 1);
                all.add(item);
            }
        }

        // 分页
        int page = parseInteger(params.getOrDefault("page", "1"));
        int size = parseInteger(params.getOrDefault("size", "10"));
        int total = all.size();
        List<Map<String, Object>> paged = all.stream()
                .skip((long) (page - 1) * size)
                .limit(size)
                .collect(Collectors.toList());

        ObjectNode root = MAPPER.createObjectNode();
        root.put("total", total);
        root.put("current", page);
        root.put("size", size);
        root.set("records", MAPPER.valueToTree(paged));
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
    }

    // ==================== 执行链 ====================

    private void handleExecute(ChannelHandlerContext ctx, String body) {
        try {
            log.info("收到执行请求 body={}", body);
            ChainExecuteRequestDTO request = MAPPER.readValue(body, ChainExecuteRequestDTO.class);
            // 引擎内部负责发布 CHAIN_STARTED / COMPLETED / FAILED 等事件
            ChainExecuteResultDTO result = chainExecutionEngine.execute(
                    request.getChainCode(), request.getParams());
            String json = MAPPER.writeValueAsString(result);
            writeResponse(ctx, HttpResponseStatus.OK, json);
        } catch (Exception e) {
            log.error("执行请求处理失败", e);
            writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"code\":500,\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // ==================== 工具方法 ====================

    private void writeResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        ByteBuf buf = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, buf);
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                .set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        ctx.writeAndFlush(response);
    }

    /** 从路径段中去掉 query string（? 及之后部分） */
    private static String stripQuery(String segment) {
        if (segment == null) return null;
        int idx = segment.indexOf('?');
        return idx >= 0 ? segment.substring(0, idx) : segment;
    }

    private Map<String, String> parseQueryParams(String uri) {
        Map<String, String> params = new LinkedHashMap<>();
        int idx = uri.indexOf('?');
        if (idx < 0) return params;
        String query = uri.substring(idx + 1);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && !kv[1].isEmpty()) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    private Integer parseInteger(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从请求体 JSON 中提取 updatedBy，body 可能为空 */
    private String extractUpdatedBy(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode json = MAPPER.readTree(body);
            if (json.has("updatedBy") && !json.get("updatedBy").isNull()) {
                return json.get("updatedBy").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private ObjectNode chainToJson(ChainPO c) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("code", c.getCode() != null ? c.getCode() : "");
        node.put("name", c.getName() != null ? c.getName() : "");
        node.put("description", c.getDescription() != null ? c.getDescription() : "");
        node.put("status", c.getStatus() != null ? c.getStatus() : 0);
        node.put("designCode", c.getDesignCode() != null ? c.getDesignCode() : "");
        node.put("version", c.getVersion() != null ? c.getVersion() : 1);
        node.put("createdBy", c.getCreatedBy() != null ? c.getCreatedBy() : "");
        node.put("updatedBy", c.getUpdatedBy() != null ? c.getUpdatedBy() : "");
        node.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt() : "");
        node.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt() : "");
        return node;
    }

    private ObjectNode designToJson(DesignPO d, List<ChainPO> bindings) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("code", d.getCode() != null ? d.getCode() : "");
        node.put("name", d.getName() != null ? d.getName() : "");
        node.put("description", d.getDescription() != null ? d.getDescription() : "");
        node.put("designer", d.getDesigner() != null ? d.getDesigner() : "");
        node.put("status", d.getStatus() != null ? d.getStatus() : 0);
        node.put("graphData", d.getGraphData() != null ? d.getGraphData() : "");
        node.put("chainData", d.getChainData() != null ? d.getChainData() : "");
        node.put("createdBy", d.getCreatedBy() != null ? d.getCreatedBy() : "");
        node.put("updatedBy", d.getUpdatedBy() != null ? d.getUpdatedBy() : "");
        node.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt() : "");
        node.put("updatedAt", d.getUpdatedAt() != null ? d.getUpdatedAt() : "");
        node.put("chainCount", bindings != null ? bindings.size() : 0);
        if (bindings != null) {
            String boundChainCodes = bindings.stream().map(ChainPO::getCode)
                    .filter(Objects::nonNull).collect(Collectors.joining(", "));
            node.put("boundChainCodes", boundChainCodes);
            ArrayNode arr = node.putArray("boundChains");
            for (ChainPO c : bindings) {
                arr.add(chainToJson(c));
            }
        }
        return node;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty 处理异常", cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.debug("连接空闲，关闭 channel");
            ctx.close();
        }
    }
}
