package com.zestflow.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.DictDataCreateDTO;
import com.zestflow.admin.model.dto.DictDataUpdateDTO;
import com.zestflow.admin.model.dto.DictTypeCreateDTO;
import com.zestflow.admin.model.dto.DictTypeUpdateDTO;
import com.zestflow.admin.model.vo.DictDataVO;
import com.zestflow.admin.model.vo.DictTypeVO;

import java.util.List;

public interface DictTypeService {

    /** 分页查询字典类型列表 */
    IPage<DictTypeVO> list(String keyword, Integer status, Integer page, Integer size);

    /** 按编码查询字典类型（含数据项列表） */
    DictTypeVO getByCode(String code);

    /** 获取全量字典数据（供其他模块下拉框使用） */
    List<DictDataVO> getDictData(String typeCode);

    /** 创建字典类型 */
    DictTypeVO create(DictTypeCreateDTO dto, String username);

    /** 更新字典类型 */
    DictTypeVO update(Long id, DictTypeUpdateDTO dto);

    /** 删除字典类型（同时删除其下数据项） */
    void delete(Long id);

    /** 切换状态 */
    void toggleStatus(Long id);

    /** 添加字典数据项 */
    DictDataVO addData(DictDataCreateDTO dto, String username);

    /** 更新字典数据项 */
    DictDataVO updateData(Long id, DictDataUpdateDTO dto);

    /** 删除字典数据项 */
    void deleteData(Long id);

    /**
     * 确保字典数据项存在（不存在则自动创建）
     * @param typeCode 字典类型编码
     * @param value    数据值
     * @param label    数据标签
     */
    void ensureDictData(String typeCode, String value, String label);
}
