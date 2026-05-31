package com.zestflow.admin.demo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.demo.model.dto.DemoSceneCreateDTO;
import com.zestflow.admin.demo.model.dto.DemoSceneUpdateDTO;
import com.zestflow.admin.demo.model.vo.DemoSceneVO;
import com.zestflow.admin.demo.service.DemoSceneService;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 演示场景管理 — CRUD
 */
@RestController
@RequestMapping("/demo/scenes")
@ConditionalOnProperty(prefix = "zestflow.demo", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class DemoSceneController {

    private final DemoSceneService sceneService;

    @GetMapping("/page")
    public Result<IPage<DemoSceneVO>> queryPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sceneService.queryPage(keyword, page, size));
    }

    @GetMapping("/list-all")
    public Result<List<DemoSceneVO>> listAll() {
        return Result.success(sceneService.listAll());
    }

    @GetMapping("/{id}")
    public Result<DemoSceneVO> getById(@PathVariable Long id) {
        DemoSceneVO vo = sceneService.getById(id);
        if (vo == null) {
            return Result.fail(404, "场景不存在");
        }
        return Result.success(vo);
    }

    @GetMapping("/code/{sceneCode}")
    public Result<DemoSceneVO> getByCode(@PathVariable String sceneCode) {
        DemoSceneVO vo = sceneService.getByCode(sceneCode);
        if (vo == null) {
            return Result.fail(404, "场景不存在");
        }
        return Result.success(vo);
    }

    @PostMapping
    public Result<DemoSceneVO> create(@RequestBody DemoSceneCreateDTO dto) {
        DemoSceneVO vo = sceneService.create(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    public Result<DemoSceneVO> update(@PathVariable Long id, @RequestBody DemoSceneUpdateDTO dto) {
        DemoSceneVO vo = sceneService.update(id, dto);
        if (vo == null) {
            return Result.fail(404, "场景不存在");
        }
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sceneService.delete(id);
        return Result.success();
    }
}
