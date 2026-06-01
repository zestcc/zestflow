package com.zestflow.admin.playground.service;

import com.zestflow.admin.playground.model.vo.PlaygroundRecordVO;
import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;

import java.util.Map;

/**
 * 试验场执行服务接口
 */
public interface PlaygroundService {

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
    PlaygroundSceneVO getSceneInfo(String sceneCode);
}
