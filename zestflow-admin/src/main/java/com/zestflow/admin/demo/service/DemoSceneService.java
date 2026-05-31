package com.zestflow.admin.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.demo.model.dto.DemoSceneCreateDTO;
import com.zestflow.admin.demo.model.dto.DemoSceneUpdateDTO;
import com.zestflow.admin.demo.model.vo.DemoSceneVO;

import java.util.List;

/**
 * 演示场景服务接口
 */
public interface DemoSceneService {

    /**
     * 分页查询场景列表
     */
    IPage<DemoSceneVO> queryPage(String keyword, int page, int size);

    /**
     * 查询所有可用场景（下拉选择）
     */
    List<DemoSceneVO> listAll();

    /**
     * 查询场景详情
     */
    DemoSceneVO getById(Long id);

    /**
     * 根据编码查询场景
     */
    DemoSceneVO getByCode(String sceneCode);

    /**
     * 创建场景
     */
    DemoSceneVO create(DemoSceneCreateDTO dto);

    /**
     * 更新场景
     */
    DemoSceneVO update(Long id, DemoSceneUpdateDTO dto);

    /**
     * 删除场景
     */
    void delete(Long id);
}
