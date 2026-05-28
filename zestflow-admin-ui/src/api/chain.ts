import http from './index'

export const chainApi = {
  list(params?: any) {
    return http.get('/chains', { params })
  },

  getById(id: number) {
    return http.get(`/chains/${id}`)
  },

  create(data: any) {
    return http.post('/chains', data)
  },

  update(id: number, data: any) {
    return http.put(`/chains/${id}`, data)
  },

  delete(id: number) {
    return http.delete(`/chains/${id}`)
  },
}
