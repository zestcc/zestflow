import request from './index'
import type { DemoSceneVO } from './demo-scene'
import type { DemoRecordVO } from './demo-record'

export interface PlaygroundExecuteResult {
  code: number
  message: string
  logId: number
  instanceId: string
  sceneName: string
  costMs: number
  status: number
  errorMsg: string
  tip: string
  logUrl?: string
  result?: Record<string, any>
}

/** 获取场景信息（含模板数据） */
export function getPlaygroundSceneInfo(sceneCode: string) {
  return request.get<DemoSceneVO>(`/demo/playground/scene/${sceneCode}`)
}

/** 执行场景 */
export function executePlaygroundScene(sceneCode: string, params?: Record<string, any>) {
  return request.post(`/demo/playground/execute/${sceneCode}`, params || {})
}

/** 查询执行历史（试验场工作区） */
export function queryPlaygroundHistory(dto: { sceneId?: number; status?: number; page?: number; size?: number }) {
  return request.post('/demo/playground/history', dto)
}

/** 查询单条历史详情 */
export function getPlaygroundHistoryDetail(id: number) {
  return request.get<DemoRecordVO>(`/demo/playground/history/${id}`)
}
