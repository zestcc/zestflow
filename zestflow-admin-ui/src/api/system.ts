import request from './index'

export interface Features {
  playground: {
    enabled: boolean
  }
}

export function getFeatures() {
  return request.get<Features>('/system/features')
}
