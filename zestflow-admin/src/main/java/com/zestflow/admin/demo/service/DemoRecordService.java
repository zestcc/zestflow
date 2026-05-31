package com.zestflow.admin.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.demo.model.dto.DemoRecordQueryDTO;
import com.zestflow.admin.demo.model.entity.DemoRecordPO;
import com.zestflow.admin.demo.model.vo.DemoRecordVO;

/**
 * 演示记录服务接口
 */
public interface DemoRecordService {

    /**
     * 分页查询执行记录
     */
    IPage<DemoRecordVO> queryPage(DemoRecordQueryDTO dto);

    /**
     * 查询单条记录详情
     */
    DemoRecordVO getById(Long id);

    /**
     * 保存执行记录
     */
    DemoRecordPO saveRecord(DemoRecordPO po);
}
