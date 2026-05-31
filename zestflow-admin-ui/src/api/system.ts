import request from './index'

export interface Features {
  demo: {
    enabled: boolean
  }
}

export function getFeatures() {
  return request.get<Features>('/system/features')
}
