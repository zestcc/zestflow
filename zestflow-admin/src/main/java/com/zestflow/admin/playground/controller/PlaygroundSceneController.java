package com.zestflow.admin.playground.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneCreateDTO;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneUpdateDTO;
import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;
import com.zestflow.admin.playground.service.PlaygroundSceneService;
import com.zestflow.admin.playground.model.vo.AvailableEndpointVO;
import com.zestflow.admin.playground.support.PlaygroundAccessControl;
import com.zestflow.admin.playground.support.PlaygroundUrlResolver;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 演示场景管理 — CRUD
 */
@Slf4j
@RestController
@RequestMapping("/api/playground/scenes")
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PlaygroundSceneController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PlaygroundSceneService sceneService;
    private final ExecutorProxyService executorProxyService;
    private final PlaygroundAccessControl accessControl;
    private final PlaygroundUrlResolver playgroundUrlResolver;

    @GetMapping("/page")
    public Result<IPage<PlaygroundSceneVO>> queryPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String appCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (org.springframework.util.StringUtils.hasText(appCode)) {
            accessControl.requireAppPermission(appCode, "APP_VIEWER");
        }
        return Result.success(sceneService.queryPage(keyword, appCode, page, size));
    }

    @GetMapping("/list-all")
    public Result<List<PlaygroundSceneVO>> listAll(@RequestParam(required = false) String appCode) {
        if (org.springframework.util.StringUtils.hasText(appCode)) {
            accessControl.requireAppPermission(appCode, "APP_VIEWER");
        }
        return Result.success(sceneService.listAll(appCode));
    }

    @GetMapping("/{id}")
    public Result<PlaygroundSceneVO> getById(@PathVariable Long id) {
        PlaygroundSceneVO vo = sceneService.getById(id);
        if (vo == null) {
            return Result.fail(404, "场景不存在");
        }
        accessControl.requireAppPermission(vo.getAppCode(), "APP_VIEWER");
        return Result.success(vo);
    }

    @GetMapping("/code/{sceneCode}")
    public Result<PlaygroundSceneVO> getByCode(@PathVariable String sceneCode) {
        PlaygroundSceneVO vo = sceneService.getByCode(sceneCode);
        if (vo == null) {
            return Result.fail(404, "场景不存在");
        }
        accessControl.requireAppPermission(vo.getAppCode(), "APP_VIEWER");
        return Result.success(vo);
    }

    @PostMapping
    public Result<PlaygroundSceneVO> create(@RequestBody PlaygroundSceneCreateDTO dto) {
        String appCode = org.springframework.util.StringUtils.hasText(dto.getAppCode())
                ? dto.getAppCode() : null;
        accessControl.requireAppPermission(
                org.springframework.util.StringUtils.hasText(appCode) ? appCode : sceneService.getDefaultAppCode(),
                "APP_EDITOR");
        PlaygroundSceneVO vo = sceneService.create(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    public Result<PlaygroundSceneVO> update(@PathVariable Long id, @RequestBody PlaygroundSceneUpdateDTO dto) {
        PlaygroundSceneVO existing = sceneService.getById(id);
        if (existing == null) {
            return Result.fail(404, "场景不存在");
        }
        accessControl.requireAppPermission(existing.getAppCode(), "APP_EDITOR");
        PlaygroundSceneVO vo = sceneService.update(id, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        PlaygroundSceneVO existing = sceneService.getById(id);
        if (existing == null) {
            return Result.fail(404, "场景不存在");
        }
        accessControl.requireAppPermission(existing.getAppCode(), "APP_ADMIN");
        sceneService.delete(id);
        return Result.success();
    }

    /**
     * 通过 appCode 查询对应 Executor 应用的控制器端点，供前端"从 Controller 导入"使用
     */
    @GetMapping("/available-endpoints")
    public Result<Map<String, Object>> getAvailableEndpoints(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String className,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (appCode == null || appCode.isBlank()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("records", Collections.emptyList());
            empty.put("total", 0);
            empty.put("page", page);
            empty.put("size", size);
            return Result.success(empty);
        }
        accessControl.requireAppPermission(appCode, "APP_VIEWER");
        StringBuilder sb = new StringBuilder("?");
        boolean hasParam = false;
        if (keyword != null && !keyword.isEmpty()) {
            sb.append("keyword=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8));
            hasParam = true;
        }
        if (className != null && !className.isEmpty()) {
            if (hasParam) sb.append("&");
            sb.append("className=").append(URLEncoder.encode(className, StandardCharsets.UTF_8));
            hasParam = true;
        }
        String query = hasParam ? sb.toString() : null;
        String json = executorProxyService.getArrayFromExecutor(appCode, "/api/endpoints", query);
        try {
            List<AvailableEndpointVO> all = MAPPER.readValue(json,
                    new TypeReference<List<AvailableEndpointVO>>() {});
            for (AvailableEndpointVO ep : all) {
                ep.setRequestPath(playgroundUrlResolver.toDisplayUrl(appCode, ep.getRequestPath()));
            }
            int total = all.size();
            int from = (page - 1) * size;
            List<AvailableEndpointVO> records = from >= total
                    ? Collections.emptyList()
                    : all.subList(from, Math.min(from + size, total));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("records", records);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            return Result.success(result);
        } catch (Exception e) {
            log.error("解析 Executor 端点列表失败 appCode={}", appCode, e);
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("records", Collections.emptyList());
            empty.put("total", 0);
            empty.put("page", page);
            empty.put("size", size);
            return Result.success(empty);
        }
    }

    /**
     * 查询指定应用的 Controller 类名列表（供导入弹窗下拉使用）
     */
    @GetMapping("/available-endpoints/classes")
    public Result<List<String>> getEndpointClasses(@RequestParam(required = false) String appCode) {
        if (appCode == null || appCode.isBlank()) {
            return Result.success(Collections.emptyList());
        }
        accessControl.requireAppPermission(appCode, "APP_VIEWER");
        String json = executorProxyService.getArrayFromExecutor(appCode, "/api/endpoints/classes", null);
        try {
            List<String> list = MAPPER.readValue(json, new TypeReference<List<String>>() {});
            return Result.success(list);
        } catch (Exception e) {
            log.error("解析 Executor 类名列表失败 appCode={}", appCode, e);
            return Result.success(Collections.emptyList());
        }
    }
}
