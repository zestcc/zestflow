package com.zestflow.admin.playground.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.playground.model.dto.PlaygroundRecordQueryDTO;
import com.zestflow.admin.playground.model.entity.PlaygroundRecordPO;
import com.zestflow.admin.playground.model.vo.PlaygroundRecordVO;
import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;
import com.zestflow.admin.playground.repository.PlaygroundRecordMapper;
import com.zestflow.admin.playground.service.PlaygroundService;
import com.zestflow.admin.playground.service.PlaygroundSceneService;
import com.zestflow.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import org.springframework.util.StringUtils;

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
    private final PlaygroundRecordMapper recordMapper;

    /**
     * 获取场景信息（含默认请求头/请求体模板）
     */
    @GetMapping("/scene/{sceneCode}")
    public Result<PlaygroundSceneVO> getSceneInfo(@PathVariable String sceneCode) {
        PlaygroundSceneVO vo = playgroundService.getSceneInfo(sceneCode);
        if (vo == null) {
            return Result.fail(404, "场景不存在");
        }
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
        return Result.success(
                recordMapper.selectPage(
                        new Page<>(dto.getPage(), dto.getSize()),
                        new LambdaQueryWrapper<PlaygroundRecordPO>()
                                .eq(dto.getSceneId() != null, PlaygroundRecordPO::getSceneId, dto.getSceneId())
                                .eq(dto.getStatus() != null, PlaygroundRecordPO::getStatus, dto.getStatus())
                                .eq(StringUtils.hasText(dto.getAppCode()), PlaygroundRecordPO::getAppCode, dto.getAppCode())
                                .orderByDesc(PlaygroundRecordPO::getCreatedAt))
                        .convert(this::toVO));
    }

    /**
     * 查询单条历史详情（可填充到请求面板进行重试）
     */
    @GetMapping("/history/{id}")
    public Result<PlaygroundRecordVO> getHistoryDetail(@PathVariable Long id) {
        PlaygroundRecordPO po = recordMapper.selectById(id);
        if (po == null) {
            return Result.fail(404, "记录不存在");
        }
        return Result.success(toVO(po));
    }

    private PlaygroundRecordVO toVO(PlaygroundRecordPO po) {
        if (po == null) return null;
        PlaygroundRecordVO vo = new PlaygroundRecordVO();
        vo.setId(po.getId());
        vo.setSceneId(po.getSceneId());
        vo.setSceneName(po.getSceneName());
        vo.setSceneCode(po.getSceneCode());
        vo.setRequestMethod(po.getRequestMethod());
        vo.setRequestPath(po.getRequestPath());
        vo.setRequestHeaders(po.getRequestHeaders());
        vo.setBodyType(po.getBodyType());
        vo.setRequestBody(po.getRequestBody());
        vo.setResponseStatus(po.getResponseStatus());
        vo.setResponseBody(po.getResponseBody());
        vo.setResponseHeaders(po.getResponseHeaders());
        vo.setChainCode(po.getChainCode());
        vo.setInstanceId(po.getInstanceId());
        vo.setStatus(po.getStatus());
        vo.setCostMs(po.getCostMs());
        vo.setErrorMsg(po.getErrorMsg());
        vo.setCreatedBy(po.getCreatedBy());
        vo.setUpdatedBy(po.getUpdatedBy());
        vo.setCreatedAt(po.getCreatedAt());
        vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
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
