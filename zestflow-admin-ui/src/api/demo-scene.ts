import request from './index'

export interface DemoSceneVO {
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

export interface DemoSceneCreateDTO {
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
}

export interface DemoSceneUpdateDTO {
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
export function queryScenePage(keyword?: string, page = 1, size = 20) {
  return request.get('/demo/scenes/page', { params: { keyword, page, size } })
}

/** 查询所有场景 */
export function listAllScenes() {
  return request.get('/demo/scenes/list-all')
}

/** 查询场景详情 */
export function getSceneById(id: number) {
  return request.get(`/demo/scenes/${id}`)
}

/** 按编码查询场景 */
export function getSceneByCode(sceneCode: string) {
  return request.get(`/demo/scenes/code/${sceneCode}`)
}

/** 创建场景 */
export function createScene(dto: DemoSceneCreateDTO) {
  return request.post('/demo/scenes', dto)
}

/** 更新场景 */
export function updateScene(id: number, dto: DemoSceneUpdateDTO) {
  return request.put(`/demo/scenes/${id}`, dto)
}

/** 删除场景 */
export function deleteScene(id: number) {
  return request.delete(`/demo/scenes/${id}`)
}
