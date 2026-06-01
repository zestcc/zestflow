package com.zestflow.admin.playground.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.playground.model.dto.PlaygroundRecordQueryDTO;
import com.zestflow.admin.playground.model.entity.PlaygroundRecordPO;
import com.zestflow.admin.playground.model.vo.PlaygroundRecordVO;

/**
 * 演示记录服务接口
 */
public interface PlaygroundRecordService {

    /**
     * 分页查询执行记录
     */
    IPage<PlaygroundRecordVO> queryPage(PlaygroundRecordQueryDTO dto);

    /**
     * 查询单条记录详情
     */
    PlaygroundRecordVO getById(Long id);

    /**
     * 保存执行记录
     */
    PlaygroundRecordPO saveRecord(PlaygroundRecordPO po);
}
