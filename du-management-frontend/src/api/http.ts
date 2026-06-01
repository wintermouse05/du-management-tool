import axios from 'axios'
import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { getApiErrorDetail } from '@/utils/apiError'

interface RetryableAxiosRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

interface RefreshResponse {
  accessToken: string
  tokenType: string
  username: string
  role: string
  userId: number
}

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

let refreshPromise: Promise<string> | null = null

const AUTH_REFRESH_PATH = '/auth/refresh'
const AUTH_NOTICE_STORAGE_KEY = 'du-auth-notice'
const POST_LOGIN_REDIRECT_STORAGE_KEY = 'du-post-login-redirect'
const AUTH_EXCLUDED_PATHS = [
  '/auth/login',
  '/auth/register',
  '/auth/forgot-password',
  '/auth/reset-password',
  AUTH_REFRESH_PATH,
]

function shouldAttemptRefresh(config?: RetryableAxiosRequestConfig): boolean {
  if (!config?.url) {
    return false
  }
  return !AUTH_EXCLUDED_PATHS.some((path) => config.url?.includes(path))
}

function applyAuthData(data: RefreshResponse) {
  localStorage.setItem('token', data.accessToken)
  localStorage.setItem('username', data.username)
  localStorage.setItem('role', data.role)
  localStorage.setItem('userId', String(data.userId))

  window.dispatchEvent(new CustomEvent('du-auth-token-refreshed', { detail: data }))
}

function clearAuthData() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('fullName')
  localStorage.removeItem('role')
  localStorage.removeItem('userId')

  window.dispatchEvent(new Event('du-auth-cleared'))
}

async function refreshAccessToken(): Promise<string> {
  const response = await axios.post<RefreshResponse>(
    `/api${AUTH_REFRESH_PATH}`,
    {},
    {
      withCredentials: true,
      headers: {
        'Content-Type': 'application/json',
      },
    },
  )

  applyAuthData(response.data)
  return response.data.accessToken
}

function rememberPostLoginRedirect() {
  const { pathname, search, hash } = window.location
  if (pathname === '/login') {
    return
  }
  sessionStorage.setItem(POST_LOGIN_REDIRECT_STORAGE_KEY, `${pathname}${search}${hash}`)
}

function redirectToLoginIfNeeded(message?: string) {
  if (message) {
    sessionStorage.setItem(AUTH_NOTICE_STORAGE_KEY, message)
  }

  rememberPostLoginRedirect()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

// Request interceptor — attach JWT token
http.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// Response interceptor — refresh on 401, then retry once
http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const status = error.response?.status
    const originalRequest = error.config as RetryableAxiosRequestConfig | undefined
    const errorMessage = getApiErrorDetail(error, 'Your session has expired. Please sign in again.')

    if (status === 401 && originalRequest && !originalRequest._retry && shouldAttemptRefresh(originalRequest)) {
      originalRequest._retry = true

      try {
        if (!refreshPromise) {
          refreshPromise = refreshAccessToken().finally(() => {
            refreshPromise = null
          })
        }

        const newToken = await refreshPromise
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
        }

        return http(originalRequest)
      } catch {
        clearAuthData()
        redirectToLoginIfNeeded(errorMessage)
      }
    }

    if (status === 401) {
      clearAuthData()
      if (shouldAttemptRefresh(originalRequest)) {
        redirectToLoginIfNeeded(errorMessage)
      }
    }

    return Promise.reject(error)
  },
)

export default http
