import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'

const { t } = i18n.global

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截器：注入 token 和语言偏好
http.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 发送当前语言，后端通过 Accept-Language 决定国际化消息
    const locale = localStorage.getItem('locale') || 'zh-CN'
    config.headers['Accept-Language'] = locale
    return config
  },
  (error) => Promise.reject(error),
)

function getErrorMessage(data: any): string {
  // 优先用 errorCode 查找 i18n 翻译
  if (data?.errorCode) {
    const key = `error.${data.errorCode}`
    const translated = t(key)
    if (translated !== key) return translated
  }
  // 回退到后端原始消息
  return data?.message || t('common.requestFailed')
}

// 响应拦截器：解包 data + 统一错误处理
http.interceptors.response.use(
  (response: AxiosResponse) => {
    const body = response.data
    const { code, errorCode, message, data } = body
    if (code && code !== 200) {
      ElMessage.error(getErrorMessage(body))
      return Promise.reject(new Error(message || ''))
    }
    // 成功响应直接返回 data 字段，调用方拿到的是真正的业务数据
    return data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push({ name: 'Login' })
      ElMessage.error(t('error.AUTH_UNAUTHORIZED'))
    } else if (error.response?.data) {
      ElMessage.error(getErrorMessage(error.response.data))
    } else {
      ElMessage.error(t('common.networkError'))
    }
    return Promise.reject(error)
  },
)

export default http
