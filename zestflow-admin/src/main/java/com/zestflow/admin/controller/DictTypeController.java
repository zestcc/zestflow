package com.zestflow.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.DictDataCreateDTO;
import com.zestflow.admin.model.dto.DictDataUpdateDTO;
import com.zestflow.admin.model.dto.DictTypeCreateDTO;
import com.zestflow.admin.model.dto.DictTypeUpdateDTO;
import com.zestflow.admin.model.vo.DictDataVO;
import com.zestflow.admin.model.vo.DictTypeVO;
import com.zestflow.admin.service.DictTypeService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dict-types")
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    @GetMapping
    public Result<IPage<DictTypeVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(dictTypeService.list(keyword, status, page, size));
    }

    @GetMapping("/{code}")
    public Result<DictTypeVO> getByCode(@PathVariable String code) {
        return Result.success(dictTypeService.getByCode(code));
    }

    @GetMapping("/{code}/data")
    public Result<List<DictDataVO>> getData(@PathVariable String code) {
        return Result.success(dictTypeService.getDictData(code));
    }

    @PostMapping
    public Result<DictTypeVO> create(@Valid @RequestBody DictTypeCreateDTO dto) {
        String username = SecurityUtils.getCurrentUsername();
        return Result.success(dictTypeService.create(dto, username));
    }

    @PutMapping("/{id}")
    public Result<DictTypeVO> update(@PathVariable Long id, @Valid @RequestBody DictTypeUpdateDTO dto) {
        return Result.success(dictTypeService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictTypeService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        dictTypeService.toggleStatus(id);
        return Result.success();
    }

    // ==================== 字典数据项 ====================

    @PostMapping("/data")
    public Result<DictDataVO> addData(@Valid @RequestBody DictDataCreateDTO dto) {
        String username = SecurityUtils.getCurrentUsername();
        return Result.success(dictTypeService.addData(dto, username));
    }

    @PutMapping("/data/{id}")
    public Result<DictDataVO> updateData(@PathVariable Long id, @Valid @RequestBody DictDataUpdateDTO dto) {
        return Result.success(dictTypeService.updateData(id, dto));
    }

    @DeleteMapping("/data/{id}")
    public Result<Void> deleteData(@PathVariable Long id) {
        dictTypeService.deleteData(id);
        return Result.success();
    }
}
