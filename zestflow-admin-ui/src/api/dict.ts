import http from './index'

export interface DictDataVO {
  id: number
  typeCode: string
  label: string
  value: string
  sort: number
  status: number
  tagType: string | null
  defaultFlag: number
  remark: string | null
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
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
  label: string
  value: string
  sort?: number
  status?: number
  tagType?: string
  defaultFlag?: number
  remark?: string
}

export interface DictDataUpdateDTO {
  label?: string
  value?: string
  sort?: number
  status?: number
  tagType?: string
  defaultFlag?: number
  remark?: string
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
  getDictData(code: string) {
    return http.get<DictDataVO[]>(`/dict-types/${code}/data`)
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
