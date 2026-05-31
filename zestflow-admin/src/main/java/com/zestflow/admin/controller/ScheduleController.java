package com.zestflow.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.ScheduleCreateDTO;
import com.zestflow.admin.model.dto.ScheduleUpdateDTO;
import com.zestflow.admin.model.vo.ScheduleLogVO;
import com.zestflow.admin.model.vo.ScheduleVO;
import com.zestflow.admin.service.ScheduleService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public Result<IPage<ScheduleVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(scheduleService.list(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<ScheduleVO> getById(@PathVariable Long id) {
        return Result.success(scheduleService.getById(id));
    }

    @PostMapping
    public Result<ScheduleVO> create(@Valid @RequestBody ScheduleCreateDTO dto) {
        String username = SecurityUtils.getCurrentUsername();
        return Result.success(scheduleService.create(dto, username));
    }

    @PutMapping("/{id}")
    public Result<ScheduleVO> update(@PathVariable Long id, @Valid @RequestBody ScheduleUpdateDTO dto) {
        return Result.success(scheduleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        scheduleService.toggleStatus(id);
        return Result.success();
    }

    @PostMapping("/{id}/trigger")
    public Result<ScheduleLogVO> trigger(@PathVariable Long id) {
        return Result.success(scheduleService.trigger(id));
    }

    @GetMapping("/logs")
    public Result<IPage<ScheduleLogVO>> listLogs(
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(scheduleService.listLogs(scheduleId, status, page, size));
    }
}
