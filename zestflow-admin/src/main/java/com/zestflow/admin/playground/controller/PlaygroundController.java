package com.zestflow.admin.playground.controller;

import com.zestflow.admin.playground.model.dto.PlaygroundRecordQueryDTO;
import com.zestflow.admin.playground.model.vo.PlaygroundRecordVO;
import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;
import com.zestflow.admin.playground.service.PlaygroundRecordService;
import com.zestflow.admin.playground.service.PlaygroundService;
import com.zestflow.admin.playground.service.PlaygroundSceneService;
import com.zestflow.admin.playground.support.PlaygroundAccessControl;
import com.zestflow.common.model.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 试验场 — API 调试工具。
 * <p>
 * 选择演示场景，自动回填请求，执行并记录。
 * 支持从历史记录加载到请求面板进行重试。
 */
@RestController
@RequestMapping("/playground")
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PlaygroundController {

    private final PlaygroundService playgroundService;
    private final PlaygroundSceneService sceneService;
    private final PlaygroundRecordService recordService;
    private final PlaygroundAccessControl accessControl;

    /**
     * 获取场景信息（含默认请求头/请求体模板）
     */
    @GetMapping("/scene/{sceneCode}")
    public Result<PlaygroundSceneVO> getSceneInfo(@PathVariable String sceneCode) {
        PlaygroundSceneVO vo = sceneService.getByCode(sceneCode);
        if (vo == null) {
            return Result.fail(404, "场景不存在");
        }
        accessControl.requireAppPermission(vo.getAppCode(), "APP_VIEWER");
        return Result.success(vo);
    }

    /**
     * 执行指定场景
     */
    @PostMapping("/execute/{sceneCode}")
    public Result<Map<String, Object>> execute(
            @PathVariable String sceneCode,
            @RequestBody(required = false) Map<String, Object> params,
            HttpServletRequest request) {

        PlaygroundSceneVO scene = sceneService.getByCode(sceneCode);
        if (scene == null) {
            return Result.fail(404, "场景不存在");
        }
        accessControl.requireAppPermission(scene.getAppCode(), "APP_EDITOR");

        String ip = resolveClientIp(request);
        Map<String, Object> result = playgroundService.executeScene(sceneCode, params, ip);
        if ((int) result.get("code") == 404 || (int) result.get("code") == 429) {
            return Result.fail((int) result.get("code"), (String) result.get("message"));
        }
        return Result.success(result);
    }

    /**
     * 查询当前用户的执行历史（含 IP 不返回）
     */
    @PostMapping("/history")
    public Result<IPage<PlaygroundRecordVO>> queryHistory(@RequestBody PlaygroundRecordQueryDTO dto) {
        if (org.springframework.util.StringUtils.hasText(dto.getAppCode())) {
            accessControl.requireAppPermission(dto.getAppCode(), "APP_VIEWER");
        }
        return Result.success(recordService.queryPage(dto));
    }

    /**
     * 查询单条历史详情（可填充到请求面板进行重试）
     */
    @GetMapping("/history/{id}")
    public Result<PlaygroundRecordVO> getHistoryDetail(@PathVariable Long id) {
        PlaygroundRecordVO vo = recordService.getById(id);
        if (vo == null) {
            return Result.fail(404, "记录不存在");
        }
        return Result.success(vo);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
