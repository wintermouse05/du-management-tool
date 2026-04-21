import http from './http'
import type { MemberRequest, MemberResponse, Page, Pageable } from '@/types'

export const membersApi = {
  getAll(params?: Pageable) {
    return http.get<Page<MemberResponse>>('/members', { params })
  },

  getById(id: number) {
    return http.get<MemberResponse>(`/members/${id}`)
  },

  create(data: MemberRequest) {
    return http.post<MemberResponse>('/members', data)
  },

  update(id: number, data: MemberRequest) {
    return http.put<MemberResponse>(`/members/${id}`, data)
  },

  deactivate(id: number) {
    return http.put<MemberResponse>(`/members/${id}/deactivate`)
  },
}
