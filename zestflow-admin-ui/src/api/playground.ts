import request from './index'

export interface SceneInfo {
  id: string
  name: string
  description: string
  defaultParams: Record<string, string>
}

export interface HistoryItem {
  id: number
  sceneId: string
  sceneName: string
  chainCode: string
  instanceId: string
  status: number
  costMs: number
  errorMsg: string
  createdAt: string
  params?: Record<string, any>
  result?: Record<string, any>
  requestHeaders?: Record<string, string>
}

export interface HistoryResult {
  list: HistoryItem[]
  total: number
  page: number
  size: number
}

export interface ExecuteResult {
  code: number
  message: string
  logId: number
  instanceId: string
  sceneName: string
  costMs: number
  status: number
  errorMsg: string
  tip: string
  result?: Record<string, any>
}

/** 获取可用演示场景列表 */
export function listScenes() {
  return request.get('/playground/scenes')
}

/** 执行指定演示场景（支持自定义参数和请求头） */
export function executeScene(sceneId: string, params: Record<string, any>, headers?: Record<string, string>) {
  const body: Record<string, any> = headers && Object.keys(headers).length > 0
    ? { params, headers }
    : params
  return request.post(`/playground/execute/${sceneId}`, body)
}

/** 查询执行历史 */
export function queryHistory(page = 1, size = 20) {
  return request.get('/playground/history', { params: { page, size } })
}

/** 查询单条执行详情 */
export function getHistoryDetail(id: number) {
  return request.get(`/playground/history/${id}`)
}
