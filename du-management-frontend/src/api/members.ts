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

  search(params?: Pageable & { q?: string; status?: string }) {
    return http.get<Page<MemberResponse>>('/members/search', { params })
  },

  exportCsv(params?: { q?: string; status?: string }) {
    return http.get<Blob>('/members/export', { params, responseType: 'blob' as const })
  },

  importFile(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<{ message: string; imported: number }>('/members/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
