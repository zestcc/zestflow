import request from './index'

export interface Features {
  playground: {
    enabled: boolean
  }
  tenant?: {
    mode: string
    ipDemoMode: string
  }
  security?: {
    registryTokenConfigured: boolean
    executorAccessTokenConfigured?: boolean
  }
  admin?: {
    deployMode: string
    cacheType: string
    redisRequired: boolean
  }
}

export function getFeatures() {
  return request.get<Features>('/system/features')
}
