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

  vote(seminarId: number, data: SeminarVoteRequest) {
    return http.post<SeminarVoteResponse>(`/seminars/${seminarId}/vote`, data)
  },

  getVotes(seminarId: number, params?: Pageable) {
    return http.get<Page<SeminarVoteResponse>>(`/seminars/${seminarId}/votes`, { params })
  },
}
