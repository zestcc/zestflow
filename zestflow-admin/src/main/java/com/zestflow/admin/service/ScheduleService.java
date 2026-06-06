package com.zestflow.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.ScheduleCreateDTO;
import com.zestflow.admin.model.dto.ScheduleUpdateDTO;
import com.zestflow.admin.model.vo.ScheduleLogVO;
import com.zestflow.admin.model.vo.ScheduleVO;

public interface ScheduleService {

    IPage<ScheduleVO> list(String keyword, String jobType, Integer status, Integer page, Integer size);

    ScheduleVO getById(Long id);

    ScheduleVO create(ScheduleCreateDTO dto, String username);

    ScheduleVO update(Long id, ScheduleUpdateDTO dto);

    void delete(Long id);

    void toggleStatus(Long id);

    ScheduleLogVO trigger(Long id);

    IPage<ScheduleLogVO> listLogs(Long scheduleId, String jobType, String keyword, Integer status, Integer page, Integer size);
}
