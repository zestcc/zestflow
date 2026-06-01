import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'

const { t } = i18n.global

// 自定义接口：响应拦截器已解包 data，故泛型返回值直接为 T（非 AxiosResponse）
interface HttpClient {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截器：注入 token 和语言偏好
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 发送当前语言，后端通过 Accept-Language 决定国际化消息
    const locale = localStorage.getItem('locale') || 'zh-CN'
    config.headers['Accept-Language'] = locale
    // 注入当前租户 ID
    const tenantId = localStorage.getItem('currentTenantId')
    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId
    }
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
instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const body = response.data
    // 非对象响应（如字符串）直接返回
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
      return body
    }
    const { code, errorCode, message, data } = body
    // code 为数字 200 → 标准 Result.success，解包返回 data
    if (typeof code === 'number' && code === 200) {
      return data
    }
    // code 为数字且非 200 → 错误响应，弹窗并 reject
    if (typeof code === 'number' && code !== 200) {
      ElMessage.error(getErrorMessage(body))
      return Promise.reject(new Error(message || ''))
    }
    // 无 code 或 code 非数字（如实体编码 CHN...）→ 代理接口裸 JSON，直接返回
    return body
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

export default instance as HttpClient
