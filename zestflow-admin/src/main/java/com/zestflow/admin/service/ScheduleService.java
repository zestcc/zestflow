package com.zestflow.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.ScheduleCreateDTO;
import com.zestflow.admin.model.dto.ScheduleUpdateDTO;
import com.zestflow.admin.model.vo.ScheduleLogVO;
import com.zestflow.admin.model.vo.ScheduleVO;

public interface ScheduleService {

    IPage<ScheduleVO> list(String keyword, Integer status, Integer page, Integer size);

    ScheduleVO getById(Long id);

    ScheduleVO create(ScheduleCreateDTO dto, String username);

    ScheduleVO update(Long id, ScheduleUpdateDTO dto);

    void delete(Long id);

    void toggleStatus(Long id);

    /** 手动触发一次调度 */
    ScheduleLogVO trigger(Long id);

    /** 查询调度日志 */
    IPage<ScheduleLogVO> listLogs(Long scheduleId, Integer status, Integer page, Integer size);
}
