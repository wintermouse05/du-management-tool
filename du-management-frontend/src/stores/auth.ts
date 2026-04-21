import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { LoginRequest, RegisterRequest } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const role = ref(localStorage.getItem('role') || '')
  const userId = ref(Number(localStorage.getItem('userId') || '0'))

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isHR = computed(() => role.value === 'HR')
  const isMember = computed(() => role.value === 'MEMBER')
  const isAdminOrHR = computed(() => isAdmin.value || isHR.value)

  function setAuth(t: string, u: string, r: string, id: number) {
    token.value = t
    username.value = u
    role.value = r
    userId.value = id
    localStorage.setItem('token', t)
    localStorage.setItem('username', u)
    localStorage.setItem('role', r)
    localStorage.setItem('userId', String(id))
  }

  function clearAuth() {
    token.value = ''
    username.value = ''
    role.value = ''
    userId.value = 0
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
    localStorage.removeItem('userId')
  }

  async function login(data: LoginRequest) {
    const res = await authApi.login(data)
    setAuth(res.data.accessToken, res.data.username, res.data.role, res.data.userId)
    return res.data
  }

  async function register(data: RegisterRequest) {
    const res = await authApi.register(data)
    setAuth(res.data.accessToken, res.data.username, res.data.role, res.data.userId)
    return res.data
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // ignore errors on logout
    } finally {
      clearAuth()
    }
  }

  return {
    token, username, role, userId,
    isAuthenticated, isAdmin, isHR, isMember, isAdminOrHR,
    login, register, logout, clearAuth,
  }
})
