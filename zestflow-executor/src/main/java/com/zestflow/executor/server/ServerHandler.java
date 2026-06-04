package com.zestflow.executor.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.zestflow.executor.engine.ExecutionIdempotencyGuard;
import com.zestflow.executor.registry.ExecutorProperties;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.zestflow.common.model.Result;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private RequestMappingHandlerMapping requestMappingHandlerMapping;
    private java.util.List<String> scanPackages = java.util.Collections.emptyList();
    /** 业务 API Tomcat/网关基址；配置后端点导入展示 absoluteUrl */
    private String playgroundBusinessBaseUrl;
    private NettyMvcDispatcher nettyMvcDispatcher;
    /** 可选 accessToken，非空时校验请求头 X-Access-Token */
    private String accessToken;
    /** 链执行业务线程池（/execute 专用，避免阻塞 Netty EventLoop） */
    private ChainExecuteThreadPool executeThreadPool;
    private ExecutionIdempotencyGuard idempotencyGuard;
    private ExecutorProperties executorProperties;
    private final AtomicBoolean acceptingExecuteRequests = new AtomicBoolean(true);

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

        // 可选 accessToken 校验（仅非 /health 请求，内网安全加固）
        if (accessToken != null && !accessToken.isEmpty() && !"/health".equals(uri)) {
            String token = request.headers().get("X-Access-Token");
            if (!accessToken.equals(token)) {
                log.warn("accessToken 校验失败 uri={}", uri);
                writeResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                        "{\"code\":401,\"message\":\"Unauthorized\"}");
                return;
            }
        }

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

        // 控制器端点列表（供 Admin 的"从 Controller 导入"使用）
        if (method == HttpMethod.GET && ("/api/endpoints".equals(uri) || uri.startsWith("/api/endpoints?"))) {
            handleListEndpoints(ctx, uri);
            return true;
        }

        // 控制器类名列表（供前端导入弹窗的 Controller 下拉）
        if (method == HttpMethod.GET && ("/api/endpoints/classes".equals(uri) || uri.startsWith("/api/endpoints/classes?"))) {
            handleListEndpointClasses(ctx);
            return true;
        }

        // Playground 运行时配置（businessBaseUrl / channel）
        if (method == HttpMethod.GET && "/api/playground/config".equals(uri)) {
            handlePlaygroundConfig(ctx);
            return true;
        }

        // 业务演示 API（/api/orders 等）：进程内转发 Spring MVC，不经 Tomcat
        if (NettyMvcDispatcher.isDispatchableBusinessPath(uri)) {
            return handleBusinessApi(ctx, method, uri, body);
        }

        return false;
    }

    private boolean handleBusinessApi(ChannelHandlerContext ctx, HttpMethod method, String uri, String body)
            throws Exception {
        if (nettyMvcDispatcher == null) {
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"业务 API 转发未启用（需 Spring MVC）\"}");
            return true;
        }
        NettyMvcDispatcher.DispatchResult result =
                nettyMvcDispatcher.dispatch(method.name(), uri, body);
        if (!result.handled()) {
            return false;
        }
        HttpResponseStatus status = toHttpStatus(result.httpStatus());
        String respBody = result.body();
        if (respBody == null || respBody.isEmpty()) {
            respBody = "{\"code\":" + result.httpStatus() + "}";
        }
        writeResponse(ctx, status, respBody);
        return true;
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
            if ("bindable".equals(action) && method == HttpMethod.GET) {
                return handleGetBindable(ctx, code);
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
        String appCode = json.has("appCode") ? json.get("appCode").asText(null) : null;
        Integer status = json.has("status") && !json.get("status").isNull() ? json.get("status").asInt() : null;
        String updatedBy = json.has("updatedBy") ? json.get("updatedBy").asText(null) : null;

        ChainPO data = chainRepo.create(name, description, appCode, status, updatedBy);
        log.info("创建链 code={} name={} appCode={} updatedBy={}", data.getCode(), name, appCode, updatedBy);
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
        String appCode = json.has("appCode") ? json.get("appCode").asText(null) : null;
        String graphData = json.has("graphData") ? json.get("graphData").asText(null) : null;
        String chainData = json.has("chainData") ? json.get("chainData").asText(null) : null;
        String updatedBy = json.has("updatedBy") ? json.get("updatedBy").asText(null) : null;

        DesignPO data = designRepo.create(name, description, designer, appCode, graphData, chainData, updatedBy);
        log.info("创建设计 code={} name={} appCode={} designer={}", data.getCode(), name, appCode, designer);
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
        boolean flowValid = false;
        if (chainLoader != null) {
            List<ChainPO> bindings = designRepo.getBindings(code);
            String sampleChainCode = bindings.isEmpty() ? code : bindings.get(0).getCode();
            List<String> errors = chainLoader.validateDesignFlow(sampleChainCode, graphData, chainData);
            flowValid = errors.isEmpty();
            if (!flowValid) {
                log.warn("设计保存时流程校验未通过 code={} errors={}", code, errors);
            }
            chainRepo.syncBoundChainStatusAfterDesignSave(code, flowValid, updatedBy);
        } else {
            chainRepo.resetBoundChainStatus(code, updatedBy);
        }
        log.info("保存设计图谱 code={} flowValid={} updatedBy={}", code, flowValid, updatedBy);
        String message = flowValid ? "保存成功" : "保存成功（流程未通过校验，关联链状态为设计中）";
        writeResponse(ctx, HttpResponseStatus.OK,
                "{\"code\":200,\"message\":\"" + message + "\",\"flowValid\":" + flowValid + "}");
        return true;
    }

    private boolean handleDeleteDesign(ChannelHandlerContext ctx, String code, String uri, String body) throws Exception {
        String updatedBy = extractUpdatedBy(body);
        if (updatedBy == null) {
            Map<String, String> params = parseQueryParams(uri);
            updatedBy = params.get("updatedBy");
        }
        List<String> boundChainCodes = designRepo.listBoundChainCodes(code);
        DesignPO removed = designRepo.delete(code, updatedBy);
        if (removed == null) {
            log.warn("删除设计不存在 code={}", code);
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    "{\"code\":404,\"message\":\"设计不存在: " + code + "\"}");
            return true;
        }
        if (chainLoader != null) {
            for (String chainCode : boundChainCodes) {
                chainLoader.unloadFromMemory(chainCode);
            }
        }
        log.info("删除设计 code={} name={} cascadedChains={} updatedBy={}",
                code, removed.getName(), boundChainCodes.size(), updatedBy);
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

    /**
     * 获取可选绑定的链列表（排除已绑定的链）
     */
    private boolean handleGetBindable(ChannelHandlerContext ctx, String designCode) throws Exception {
        List<ChainPO> allChains = chainRepo.list(null, null);
        List<ChainPO> boundChains = designRepo.getBindings(designCode);
        Set<String> boundCodes = boundChains.stream().map(ChainPO::getCode).filter(Objects::nonNull).collect(Collectors.toSet());
        List<ChainPO> bindable = allChains.stream()
                .filter(c -> !boundCodes.contains(c.getCode()))
                .collect(Collectors.toList());
        log.info("查询可选绑定 designCode={} all={} bound={} bindable={}", designCode, allChains.size(), boundChains.size(), bindable.size());
        ArrayNode arr = MAPPER.createArrayNode();
        for (ChainPO c : bindable) {
            arr.add(chainToJson(c));
        }
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(arr));
        return true;
    }

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

    /**
     * 优雅关闭 — 拒绝新的 /execute，在途请求由线程池与引擎 destroy 宽限期消化。
     */
    public void beginShutdown() {
        acceptingExecuteRequests.set(false);
        log.info("Executor 已停止接受新的 /execute 请求");
    }

    private void handleExecute(ChannelHandlerContext ctx, String body) {
        if (!acceptingExecuteRequests.get()) {
            writeResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    "{\"code\":503,\"message\":\"executor shutting down\"}");
            return;
        }
        if (executeThreadPool != null) {
            executeThreadPool.execute(() -> doHandleExecute(ctx, body));
            return;
        }
        doHandleExecute(ctx, body);
    }

    private void doHandleExecute(ChannelHandlerContext ctx, String body) {
        try {
            log.info("收到执行请求 body={}", body);
            ChainExecuteRequestDTO request = MAPPER.readValue(body, ChainExecuteRequestDTO.class);
            ChainExecuteResultDTO result = executeWithIdempotency(request);
            String json = MAPPER.writeValueAsString(result);
            writeResponse(ctx, HttpResponseStatus.OK, json);
        } catch (Exception e) {
            log.error("执行请求处理失败", e);
            writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"code\":500,\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private ChainExecuteResultDTO executeWithIdempotency(ChainExecuteRequestDTO request) {
        if (idempotencyGuard == null || executorProperties == null || !executorProperties.isIdempotencyEnabled()) {
            return chainExecutionEngine.execute(request.getChainCode(), request.getParams());
        }
        String key = request.resolveIdempotencyKey();
        return idempotencyGuard.execute(
                key,
                executorProperties.getIdempotencyTtlMs(),
                executorProperties.getIdempotencyWaitMs(),
                () -> chainExecutionEngine.execute(request.getChainCode(), request.getParams()));
    }

    // ==================== 端点扫描 ====================

    /**
     * 扫描当前应用所有 Spring MVC 控制器的请求映射，返回端点列表
     */
    private boolean handleListEndpoints(ChannelHandlerContext ctx, String uri) throws Exception {
        if (requestMappingHandlerMapping == null) {
            writeResponse(ctx, HttpResponseStatus.OK, "[]");
            return true;
        }

        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        List<EndpointInfo> list = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();

            // 扫描包范围过滤
            if (!scanPackages.isEmpty()) {
                String fullName = handler.getBeanType().getName();
                boolean matched = scanPackages.stream().anyMatch(fullName::startsWith);
                if (!matched) continue;
            }

            // 提取路径
            String path = "";
            if (info.getPathPatternsCondition() != null) {
                path = info.getPathPatternsCondition().getFirstPattern().getPatternString();
            } else if (info.getPatternsCondition() != null) {
                path = info.getPatternsCondition().getPatterns().stream().findFirst().orElse("");
            }

            // 端点列表仅返回相对路径，执行统一经 Executor Netty 端口

            // 提取请求方法
            Set<RequestMethod> httpMethods = info.getMethodsCondition() != null
                    ? info.getMethodsCondition().getMethods() : Collections.emptySet();
            String method = httpMethods.stream().findFirst()
                    .map(Enum::name).orElse("ALL");

            // 参数类型名列表
            List<String> params = Arrays.stream(handler.getMethod().getParameters())
                    .map(p -> p.getParameterizedType().getTypeName())
                    .collect(Collectors.toList());

            // 是否有 @RequestBody，并提取 DTO 类名 + 生成模板 JSON
            boolean hasBody = false;
            String requestBodyType = "";
            String requestBodyTemplate = "";
            for (var p : handler.getMethod().getParameters()) {
                if (p.isAnnotationPresent(RequestBody.class)) {
                    hasBody = true;
                    Class<?> paramType = p.getType();
                    String fullName = paramType.getName();
                    int dot = fullName.lastIndexOf('.');
                    requestBodyType = dot >= 0 ? fullName.substring(dot + 1) : fullName;
                    requestBodyTemplate = generateBodyTemplate(paramType);
                    break;
                }
            }

            // 提取响应体类型 + 生成响应示例模板
            String responseBodyType = "";
            String responseBodyTemplate = "";
            java.lang.reflect.Method handlerMethod = handler.getMethod();
            Type genericReturnType = handlerMethod.getGenericReturnType();
            if (genericReturnType != null) {
                responseBodyTemplate = generateResponseTemplate(genericReturnType);
                // 从响应模板中推断类型名（如果含 data 字段则是 Result 包装）
                if (!responseBodyTemplate.isEmpty()) {
                    responseBodyType = tryExtractResponseTypeName(genericReturnType);
                }
            }

            // 提取请求头信息
            String requestHeaders = extractRequestHeaders(handler, info);

            list.add(new EndpointInfo(
                    handler.getBeanType().getSimpleName(),
                    handler.getMethod().getName(),
                    path, method, params, hasBody, requestBodyType, requestBodyTemplate,
                    responseBodyType, responseBodyTemplate, requestHeaders
            ));
        }

        // 排序
        list.sort(Comparator.comparing(EndpointInfo::getClassName)
                .thenComparing(EndpointInfo::getRequestPath));

        // 关键字过滤
        Map<String, String> paramsMap = parseQueryParams(uri);
        String keyword = paramsMap.get("keyword");
        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.toLowerCase();
            list = list.stream()
                    .filter(e -> e.getClassName().toLowerCase().contains(kw)
                            || e.getRequestPath().toLowerCase().contains(kw)
                            || e.getMethodName().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
        }

        // Controller 类名过滤
        String classNameFilter = paramsMap.get("className");
        if (classNameFilter != null && !classNameFilter.isEmpty()) {
            list = list.stream()
                    .filter(e -> e.getClassName().equals(classNameFilter))
                    .collect(Collectors.toList());
        }

        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(list));
        return true;
    }

    /**
     * 返回唯一 Controller 类名列表（供前端导入弹窗下拉使用）
     */
    private boolean handleListEndpointClasses(ChannelHandlerContext ctx) throws Exception {
        if (requestMappingHandlerMapping == null) {
            writeResponse(ctx, HttpResponseStatus.OK, "[]");
            return true;
        }

        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        java.util.Set<String> classNames = new java.util.LinkedHashSet<>();

        for (HandlerMethod handler : handlerMethods.values()) {
            String fullName = handler.getBeanType().getName();
            // 扫描包范围过滤
            if (!scanPackages.isEmpty()) {
                boolean matched = scanPackages.stream().anyMatch(fullName::startsWith);
                if (!matched) continue;
            }
            classNames.add(handler.getBeanType().getSimpleName());
        }

        List<String> sorted = new ArrayList<>(classNames);
        Collections.sort(sorted);
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(sorted));
        return true;
    }

    private boolean handlePlaygroundConfig(ChannelHandlerContext ctx) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        String base = playgroundBusinessBaseUrl != null ? playgroundBusinessBaseUrl.trim() : "";
        root.put("businessBaseUrl", base);
        root.put("channel", base.isEmpty() ? "netty" : "tomcat");
        writeResponse(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
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

    private static HttpResponseStatus toHttpStatus(int code) {
        try {
            return HttpResponseStatus.valueOf(code);
        } catch (IllegalArgumentException e) {
            if (code >= 500) {
                return HttpResponseStatus.INTERNAL_SERVER_ERROR;
            }
            if (code >= 400) {
                return HttpResponseStatus.BAD_REQUEST;
            }
            return HttpResponseStatus.OK;
        }
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
        node.put("appCode", c.getAppCode() != null ? c.getAppCode() : "");
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
        node.put("appCode", d.getAppCode() != null ? d.getAppCode() : "");
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

    // ==================== 请求体/响应体模板生成 ====================

    /**
     * 通过反射读取 DTO 字段，生成请求体示例 JSON（入口）
     */
    private static String generateBodyTemplate(Class<?> paramType) {
        return generateBodyTemplateRecursive(paramType, new HashSet<>(), 0);
    }

    /**
     * 递归生成请求体模板
     *
     * @param visited 已访问类型集合（防循环引用）
     * @param depth   当前缩进层级
     */
    private static String generateBodyTemplateRecursive(Class<?> paramType, Set<Class<?>> visited, int depth) {
        if (paramType == null || paramType == Object.class
                || paramType.isPrimitive() || paramType.getName().startsWith("java.")) {
            return "{}";
        }
        if (!visited.add(paramType)) {
            return "{}"; // 循环引用保护
        }

        try {
            List<Field> fields = new ArrayList<>();
            Class<?> current = paramType;
            while (current != null && current != Object.class) {
                for (Field f : current.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) {
                        fields.add(f);
                    }
                }
                current = current.getSuperclass();
            }

            if (fields.isEmpty()) return "{}";

            String indent = getIndent(depth);
            String childIndent = getIndent(depth + 1);
            StringBuilder sb = new StringBuilder("{\n");
            for (int i = 0; i < fields.size(); i++) {
                Field f = fields.get(i);
                sb.append(childIndent).append("\"").append(f.getName()).append("\": ")
                        .append(getFieldDefaultValue(f, visited, depth + 1));
                if (i < fields.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(indent).append("}");
            return sb.toString();
        } finally {
            visited.remove(paramType);
        }
    }

    /**
     * 获取字段的默认值 JSON（递归展开嵌套对象和泛型集合）
     */
    private static String getFieldDefaultValue(Field field, Set<Class<?>> visited, int depth) {
        Class<?> type = field.getType();
        String indent = getIndent(depth);

        // 基本类型和 JDK 内置类型
        if (type == String.class) return "\"\"";
        if (type == boolean.class || type == Boolean.class) return "false";
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class) return "0";
        if (type == float.class || type == Float.class
                || type == double.class || type == Double.class) return "0.0";
        if (type == BigDecimal.class) return "0";
        if (type == Date.class || type == LocalDate.class || type == LocalDateTime.class) return "\"\"";
        if (type.isEnum()) return "\"\"";
        // 其他 java.* 类型（不含 Collection/Map）
        if (type.getName().startsWith("java.") && !Collection.class.isAssignableFrom(type) && !Map.class.isAssignableFrom(type)) {
            return "\"\"";
        }

        // Map
        if (Map.class.isAssignableFrom(type)) return "{}";

        // Collection/Array — 检查泛型参数以展开嵌套类型
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                Type[] actualTypeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                if (actualTypeArgs.length > 0 && actualTypeArgs[0] instanceof Class) {
                    Class<?> elementClass = (Class<?>) actualTypeArgs[0];
                    // 仅对非 JDK 类型递归展开
                    if (!elementClass.getName().startsWith("java.") && !elementClass.isPrimitive() && !elementClass.isEnum()) {
                        String nested = generateBodyTemplateRecursive(elementClass, visited, depth + 1);
                        String nextIndent = getIndent(depth + 1);
                        return "[\n" + nextIndent + nested + "\n" + indent + "]";
                    }
                }
            }
            return "[]";
        }

        // 自定义对象类型 — 递归展开
        if (!visited.contains(type)) {
            return generateBodyTemplateRecursive(type, visited, depth);
        }
        return "{}";
    }

    /**
     * 生成缩进字符串
     */
    private static String getIndent(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("  ");
        return sb.toString();
    }

    // ==================== 响应体示例生成 ====================

    /**
     * 从 Controller 方法的泛型返回类型生成响应体示例 JSON
     */
    private static String generateResponseTemplate(Type genericReturnType) {
        if (genericReturnType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericReturnType;
            Class<?> rawType = (Class<?>) pt.getRawType();
            Type[] args = pt.getActualTypeArguments();

            // Result<T> 包装 — 生成 {"code":200, "message":"success", "data": <展开的 T>}
            if (rawType == Result.class && args.length > 0) {
                return generateWrappedResponse(args[0]);
            }

            // ResponseEntity<T> 同样处理
            if ("ResponseEntity".equals(rawType.getSimpleName()) && args.length > 0) {
                return generateWrappedResponse(args[0]);
            }

            // 其他 ParameterizedType — 用 raw type 生成
            return generateBodyTemplate(rawType);
        }

        if (genericReturnType instanceof Class) {
            Class<?> returnClass = (Class<?>) genericReturnType;
            if (returnClass == void.class || returnClass == Void.class) return "";
            if (returnClass.getName().startsWith("java.") || returnClass.isPrimitive()) return "";
            return generateBodyTemplate(returnClass);
        }

        return "";
    }

    /**
     * 将数据模板包装在 Result 响应结构中
     */
    private static String generateWrappedResponse(Type dataType) {
        String dataTemplate = generateTemplateForType(dataType, new HashSet<>(), 0);
        return "{\n" +
                "  \"code\": 200,\n" +
                "  \"message\": \"success\",\n" +
                "  \"data\": " + dataTemplate + "\n" +
                "}";
    }

    /**
     * 为任意 Type 生成示例模板（处理 Class、ParameterizedType 等）
     */
    private static String generateTemplateForType(Type type, Set<Class<?>> visited, int depth) {
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (clazz == void.class || clazz == Void.class) return "\"\"";
            if (clazz.isPrimitive() || clazz == String.class || clazz.isEnum()
                    || clazz == BigDecimal.class || clazz == Date.class
                    || clazz == LocalDate.class || clazz == LocalDateTime.class) {
                return getPrimitiveDefault(clazz);
            }
            if (clazz.isArray()) return "[]";
            if (Collection.class.isAssignableFrom(clazz)) return "[]";
            if (Map.class.isAssignableFrom(clazz)) return "{}";
            if (clazz.getName().startsWith("java.")) return "\"\"";
            return generateBodyTemplateRecursive(clazz, visited, depth);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Class<?> rawType = (Class<?>) pt.getRawType();
            Type[] args = pt.getActualTypeArguments();

            if (Collection.class.isAssignableFrom(rawType) && args.length > 0) {
                String elemTemplate = generateTemplateForType(args[0], visited, depth + 1);
                String childIndent = getIndent(depth + 1);
                String indent = getIndent(depth);
                return "[\n" + childIndent + elemTemplate + "\n" + indent + "]";
            }
            if (Map.class.isAssignableFrom(rawType)) return "{}";
            if (rawType.getName().startsWith("java.") || rawType.isPrimitive()) return "\"\"";
            return generateBodyTemplateRecursive(rawType, visited, depth);
        }
        return "\"\"";
    }

    /**
     * 从泛型返回类型中提取数据类型的类名
     */
    private static String tryExtractResponseTypeName(Type genericReturnType) {
        if (genericReturnType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericReturnType;
            Class<?> rawType = (Class<?>) pt.getRawType();
            if (rawType == Result.class || "ResponseEntity".equals(rawType.getSimpleName())) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 0) {
                    return extractTypeName(args[0]);
                }
            }
        }
        if (genericReturnType instanceof Class) {
            return ((Class<?>) genericReturnType).getSimpleName();
        }
        return "";
    }

    /**
     * 递归提取类型名称（处理 List<OrderResponse> → "OrderResponse"）
     */
    private static String extractTypeName(Type type) {
        if (type instanceof Class) {
            return ((Class<?>) type).getSimpleName();
        }
        if (type instanceof ParameterizedType) {
            Class<?> rawType = (Class<?>) ((ParameterizedType) type).getRawType();
            if (Collection.class.isAssignableFrom(rawType)) {
                Type[] args = ((ParameterizedType) type).getActualTypeArguments();
                if (args.length > 0) {
                    return "List<" + extractTypeName(args[0]) + ">";
                }
            }
            return rawType.getSimpleName();
        }
        return "";
    }

    /**
     * 基础类型的默认值 JSON 字符串
     */
    private static String getPrimitiveDefault(Class<?> type) {
        if (type == String.class) return "\"\"";
        if (type == boolean.class || type == Boolean.class) return "false";
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class) return "0";
        if (type == float.class || type == Float.class
                || type == double.class || type == Double.class) return "0.0";
        if (type == BigDecimal.class) return "0";
        if (type == Date.class || type == LocalDate.class || type == LocalDateTime.class) return "\"\"";
        if (type.isEnum()) return "\"\"";
        return "\"\"";
    }

    // ==================== 请求头提取 ====================

    /**
     * 从 Controller 方法中提取请求头信息
     */
    private static String extractRequestHeaders(HandlerMethod handler, RequestMappingInfo info) {
        List<String> headers = new ArrayList<>();

        // 从 @RequestMapping(headers = ...) 中提取
        RequestMapping classMapping = handler.getBeanType().getAnnotation(RequestMapping.class);
        if (classMapping != null) {
            for (String h : classMapping.headers()) {
                if (!h.isEmpty()) headers.add(h);
            }
        }
        RequestMapping methodMapping = handler.getMethodAnnotation(RequestMapping.class);
        if (methodMapping != null) {
            for (String h : methodMapping.headers()) {
                if (!h.isEmpty()) headers.add(h);
            }
        }

        // 从 @RequestHeader 参数中提取
        for (var p : handler.getMethod().getParameters()) {
            RequestHeader rh = p.getAnnotation(RequestHeader.class);
            if (rh != null) {
                String name = rh.value();
                if (name.isEmpty()) name = rh.name();
                if (name.isEmpty()) name = p.getName();
                boolean required = rh.required();
                headers.add(name + (required ? "" : "(可选)"));
            }
        }

        return headers.isEmpty() ? "" : String.join(", ", headers);
    }
}
