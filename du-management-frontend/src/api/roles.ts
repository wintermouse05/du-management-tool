import http from './http'
import type { RoleRequest, RoleResponse, Page, Pageable } from '@/types'

export const rolesApi = {
  getAll(params?: Pageable) {
    return http.get<Page<RoleResponse>>('/roles', { params })
  },

  getById(id: number) {
    return http.get<RoleResponse>(`/roles/${id}`)
  },

  create(data: RoleRequest) {
    return http.post<RoleResponse>('/roles', data)
  },

  update(id: number, data: RoleRequest) {
    return http.put<RoleResponse>(`/roles/${id}`, data)
  },

  delete(id: number) {
    return http.delete<void>(`/roles/${id}`)
  },
}
