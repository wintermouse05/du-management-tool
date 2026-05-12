import http from './http'
import type { ForgotPasswordRequest, LoginRequest, LoginResponse, RegisterRequest, ResetPasswordRequest } from '@/types'

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

  forgotPassword(data: ForgotPasswordRequest) {
    return http.post<void>('/auth/forgot-password', data)
  },

  resetPassword(data: ResetPasswordRequest) {
    return http.post<LoginResponse>('/auth/reset-password', data)
  },
}
