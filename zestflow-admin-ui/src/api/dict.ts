import http from './index'

export interface DictDataVO {
  id: number
  typeCode: string
  parentId?: number | null
  parentTypeCode?: string | null
  parentValue?: string | null
  label: string
  value: string
  sort: number
  status: number
  tagType: string | null
  defaultFlag: number
  remark: string | null
  extra?: string | null
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface DictDataTreeVO extends DictDataVO {
  nodeKey: string
  virtualNode?: boolean
  children?: DictDataTreeVO[]
}

export interface DictTypeVO {
  id: number
  code: string
  name: string
  description: string | null
  status: number
  sort: number
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
  dataList?: DictDataVO[]
}

export interface DictTypeCreateDTO {
  code: string
  name: string
  description?: string
  status?: number
  sort?: number
}

export interface DictTypeUpdateDTO {
  name?: string
  description?: string
  status?: number
  sort?: number
}

export interface DictDataCreateDTO {
  typeCode: string
  parentId?: number
  parentTypeCode?: string
  parentValue?: string
  label: string
  value: string
  sort?: number
  status?: number
  tagType?: string
  defaultFlag?: number
  remark?: string
  extra?: string
}

export interface DictDataUpdateDTO {
  label?: string
  value?: string
  parentId?: number | null
  parentTypeCode?: string
  parentValue?: string
  sort?: number
  status?: number
  tagType?: string
  defaultFlag?: number
  remark?: string
  extra?: string
}

export interface PageRes<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export const dictApi = {
  list(params?: { keyword?: string; status?: number; page?: number; size?: number }) {
    return http.get<PageRes<DictTypeVO>>('/dict-types', { params })
  },
  getByCode(code: string) {
    return http.get<DictTypeVO>(`/dict-types/${code}`)
  },
  getDictData(code: string, params?: { parentTypeCode?: string; parentValue?: string }) {
    return http.get<DictDataVO[]>(`/dict-types/${code}/data`, { params })
  },
  getDictDataTree(code: string) {
    return http.get<DictDataTreeVO[]>(`/dict-types/${code}/data/tree`)
  },
  create(dto: DictTypeCreateDTO) {
    return http.post<DictTypeVO>('/dict-types', dto)
  },
  update(id: number, dto: DictTypeUpdateDTO) {
    return http.put<DictTypeVO>(`/dict-types/${id}`, dto)
  },
  delete(id: number) {
    return http.delete<void>(`/dict-types/${id}`)
  },
  toggleStatus(id: number) {
    return http.put<void>(`/dict-types/${id}/status`, {})
  },
  addData(dto: DictDataCreateDTO) {
    return http.post<DictDataVO>('/dict-types/data', dto)
  },
  updateData(id: number, dto: DictDataUpdateDTO) {
    return http.put<DictDataVO>(`/dict-types/data/${id}`, dto)
  },
  deleteData(id: number) {
    return http.delete<void>(`/dict-types/data/${id}`)
  },
}
