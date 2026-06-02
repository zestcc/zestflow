package com.zestflow.admin.playground.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneCreateDTO;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneUpdateDTO;
import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;

import java.util.List;

/**
 * 演示场景服务接口
 */
public interface PlaygroundSceneService {

    /**
     * 分页查询场景列表
     */
    IPage<PlaygroundSceneVO> queryPage(String keyword, String appCode, int page, int size);

    /**
     * 查询所有可用场景（下拉选择）
     */
    List<PlaygroundSceneVO> listAll(String appCode);

    /**
     * 查询场景详情
     */
    PlaygroundSceneVO getById(Long id);

    /**
     * 根据编码查询场景
     */
    PlaygroundSceneVO getByCode(String sceneCode);

    /**
     * 创建场景
     */
    PlaygroundSceneVO create(PlaygroundSceneCreateDTO dto);

    /**
     * 更新场景
     */
    PlaygroundSceneVO update(Long id, PlaygroundSceneUpdateDTO dto);

    /**
     * 删除场景
     */
    void delete(Long id);

    /** 配置中的默认应用编码（创建场景未指定 appCode 时使用） */
    String getDefaultAppCode();
}
