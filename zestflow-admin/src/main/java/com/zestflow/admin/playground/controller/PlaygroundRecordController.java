package com.zestflow.admin.playground.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.playground.model.dto.PlaygroundRecordQueryDTO;
import com.zestflow.admin.playground.model.vo.PlaygroundRecordVO;
import com.zestflow.admin.playground.service.PlaygroundRecordService;
import com.zestflow.admin.playground.support.PlaygroundAccessControl;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 演示记录查询 — 只读
 */
@RestController
@RequestMapping("/api/playground/records")
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PlaygroundRecordController {

    private final PlaygroundRecordService recordService;
    private final PlaygroundAccessControl accessControl;

    @PostMapping("/page")
    public Result<IPage<PlaygroundRecordVO>> queryPage(@RequestBody PlaygroundRecordQueryDTO dto) {
        if (StringUtils.hasText(dto.getAppCode())) {
            accessControl.requireAppPermission(dto.getAppCode(), "APP_VIEWER");
        }
        return Result.success(recordService.queryPage(dto));
    }

    @GetMapping("/{id}")
    public Result<PlaygroundRecordVO> getById(@PathVariable Long id) {
        PlaygroundRecordVO vo = recordService.getById(id);
        if (vo == null) {
            return Result.fail(404, "记录不存在");
        }
        return Result.success(vo);
    }
}
