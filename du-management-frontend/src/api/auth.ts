import http from './http'
import type { LoginRequest, LoginResponse, RegisterRequest } from '@/types'

export const authApi = {
  login(data: LoginRequest) {
    return http.post<LoginResponse>('/auth/login', data)
  },

  register(data: RegisterRequest) {
    return http.post<LoginResponse>('/auth/register', data)
  },

  logout() {
    return http.post<void>('/auth/logout')
  },
}
