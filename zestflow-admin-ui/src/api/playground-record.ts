import request from './index'

export interface PlaygroundRecordVO {
  id: number
  sceneId: number
  sceneName: string
  sceneCode: string
  requestMethod: string
  requestPath: string
  requestHeaders: string
  bodyType: string
  requestBody: string
  responseStatus: number
  responseBody: string
  responseHeaders: string
  chainCode: string
  instanceId: string
  status: number
  costMs: number
  errorMsg: string
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export interface PlaygroundRecordQueryDTO {
  sceneId?: number
  sceneCode?: string
  sceneName?: string
  chainCode?: string
  status?: number
  keyword?: string
  appCode?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}

/** 分页查询执行记录 */
export function queryRecordPage(dto: PlaygroundRecordQueryDTO) {
  return request.post('/playground/records/page', dto)
}

/** 查询单条记录详情 */
export function getRecordById(id: number) {
  return request.get(`/playground/records/${id}`)
}
