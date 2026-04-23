import http from './http'
import type {
  SeminarRequest, SeminarResponse, SeminarVoteRequest,
  SeminarVoteResponse, Page, Pageable
} from '@/types'

export const seminarsApi = {
  getAll(params?: Pageable) {
    return http.get<Page<SeminarResponse>>('/seminars', { params })
  },

  getById(id: number) {
    return http.get<SeminarResponse>(`/seminars/${id}`)
  },

  create(data: SeminarRequest) {
    return http.post<SeminarResponse>('/seminars', data)
  },

  update(id: number, data: SeminarRequest) {
    return http.put<SeminarResponse>(`/seminars/${id}`, data)
  },

  uploadMaterials(id: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<SeminarResponse>(`/seminars/${id}/materials`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  downloadMaterials(id: number) {
    return http.get<Blob>(`/seminars/${id}/materials`, {
      responseType: 'blob',
    })
  },

  vote(seminarId: number, data: SeminarVoteRequest) {
    return http.post<SeminarVoteResponse>(`/seminars/${seminarId}/vote`, data)
  },

  getVotes(seminarId: number, params?: Pageable) {
    return http.get<Page<SeminarVoteResponse>>(`/seminars/${seminarId}/votes`, { params })
  },
}
