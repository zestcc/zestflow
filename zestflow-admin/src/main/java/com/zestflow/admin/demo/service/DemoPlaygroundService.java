package com.zestflow.admin.demo.service;

import com.zestflow.admin.demo.model.vo.DemoRecordVO;
import com.zestflow.admin.demo.model.vo.DemoSceneVO;

import java.util.Map;

/**
 * 试验场执行服务接口
 */
public interface DemoPlaygroundService {

    /**
     * 执行指定场景的链
     *
     * @param sceneCode 场景编码
     * @param params    用户传入的参数
     * @return 执行结果
     */
    Map<String, Object> executeScene(String sceneCode, Map<String, Object> params, String requestIp);

    /**
     * 查询场景信息（含场景默认数据）
     */
    DemoSceneVO getSceneInfo(String sceneCode);
}
