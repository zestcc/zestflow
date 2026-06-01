import request from './index'

export interface PlaygroundSceneVO {
  id: number
  sceneCode: string
  name: string
  description: string
  requestPath: string
  requestMethod: string
  requestHeaders: string
  bodyType: string
  requestBody: string
  responseExample: string
  chainCode: string
  rateLimit: number
  appCode: string
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export interface PlaygroundSceneCreateDTO {
  name: string
  description?: string
  requestPath: string
  requestMethod?: string
  requestHeaders?: string
  bodyType?: string
  requestBody?: string
  responseExample?: string
  chainCode: string
  rateLimit?: number
  appCode?: string
}

export interface PlaygroundSceneUpdateDTO {
  name?: string
  description?: string
  requestPath?: string
  requestMethod?: string
  requestHeaders?: string
  bodyType?: string
  requestBody?: string
  responseExample?: string
  chainCode?: string
  rateLimit?: number
}

/** 分页查询场景列表 */
export function queryPlaygroundScenePage(keyword?: string, appCode?: string, page = 1, size = 20) {
  return request.get('/playground/scenes/page', { params: { keyword, appCode, page, size } })
}

/** 查询所有场景 */
export function listAllPlaygroundScenes(appCode?: string) {
  return request.get('/playground/scenes/list-all', { params: { appCode } })
}

/** 查询场景详情 */
export function getPlaygroundSceneById(id: number) {
  return request.get(`/playground/scenes/${id}`)
}

/** 按编码查询场景 */
export function getPlaygroundSceneByCode(sceneCode: string) {
  return request.get(`/playground/scenes/code/${sceneCode}`)
}

/** 创建场景 */
export function createPlaygroundScene(dto: PlaygroundSceneCreateDTO) {
  return request.post('/playground/scenes', dto)
}

/** 更新场景 */
export function updatePlaygroundScene(id: number, dto: PlaygroundSceneUpdateDTO) {
  return request.put(`/playground/scenes/${id}`, dto)
}

/** 删除场景 */
export function deletePlaygroundScene(id: number) {
  return request.delete(`/playground/scenes/${id}`)
}

/** 可用端点信息 */
export interface AvailableEndpoint {
  className: string
  methodName: string
  requestPath: string
  requestMethod: string
  parameters: string[]
  hasRequestBody: boolean
  requestBodyType: string
  requestBodyTemplate: string
  responseBodyType: string
  responseBodyTemplate: string
  requestHeaders: string
}

/** 扫描指定应用的 Controller 可用端点（分页） */
export function getAvailableEndpoints(appCode?: string, keyword?: string, className?: string, page = 1, size = 10) {
  return request.get('/playground/scenes/available-endpoints', { params: { appCode, keyword, className, page, size } })
}

/** 查询指定应用的 Controller 类名列表（供导入弹窗下拉使用） */
export function getEndpointClasses(appCode?: string) {
  return request.get('/playground/scenes/available-endpoints/classes', { params: { appCode } })
}
