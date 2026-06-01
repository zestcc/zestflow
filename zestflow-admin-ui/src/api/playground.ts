import request from './index'
import type { PlaygroundSceneVO } from './playground-scene'

export interface PlaygroundRecordVO {
  id: number
  sceneCode: string
  sceneName: string
  chainCode: string
  status: number
  costMs: number
  createdAt: string
  requestMethod: string
  requestPath: string
  requestHeaders: string
  requestBody: string
  responseBody: string
  responseStatus: number
  errorMsg: string
  instanceId: string
  bodyType: string
  createdBy: string
  updatedBy: string
  updatedAt: string
}

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
  return request.get<PlaygroundSceneVO>(`/playground/scene/${sceneCode}`)
}

/** 执行场景 */
export function executePlaygroundScene(sceneCode: string, params?: Record<string, any>) {
  return request.post(`/playground/execute/${sceneCode}`, params || {})
}

/** 查询执行历史（试验场工作区） */
export function queryPlaygroundHistory(dto: { sceneId?: number; status?: number; appCode?: string; page?: number; size?: number }) {
  return request.post('/playground/history', dto)
}

/** 查询单条历史详情 */
export function getPlaygroundHistoryDetail(id: number) {
  return request.get<PlaygroundRecordVO>(`/playground/history/${id}`)
}
