package com.zestflow.admin.demo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.demo.model.dto.DemoRecordQueryDTO;
import com.zestflow.admin.demo.model.vo.DemoRecordVO;
import com.zestflow.admin.demo.service.DemoRecordService;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * 演示记录查询 — 只读
 */
@RestController
@RequestMapping("/demo/records")
@ConditionalOnProperty(prefix = "zestflow.demo", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class DemoRecordController {

    private final DemoRecordService recordService;

    @PostMapping("/page")
    public Result<IPage<DemoRecordVO>> queryPage(@RequestBody DemoRecordQueryDTO dto) {
        return Result.success(recordService.queryPage(dto));
    }

    @GetMapping("/{id}")
    public Result<DemoRecordVO> getById(@PathVariable Long id) {
        DemoRecordVO vo = recordService.getById(id);
        if (vo == null) {
            return Result.fail(404, "记录不存在");
        }
        return Result.success(vo);
    }
}
