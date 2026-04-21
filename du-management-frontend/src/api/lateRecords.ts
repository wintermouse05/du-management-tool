import http from './http'
import type {
  LateRecordRequest, LateRecordResponse,
  LateSummaryResponse, Page, Pageable
} from '@/types'

export const lateRecordsApi = {
  create(data: LateRecordRequest) {
    return http.post<LateRecordResponse>('/late-records', data)
  },

  getAll(params?: Pageable) {
    return http.get<Page<LateRecordResponse>>('/late-records', { params })
  },

  getByUser(userId: number, params?: Pageable) {
    return http.get<Page<LateRecordResponse>>('/late-records/by-user', {
      params: { userId, ...params },
    })
  },

  getMonthlySummary(year: number, month: number, params?: Pageable) {
    return http.get<Page<LateSummaryResponse>>('/late-records/monthly-summary', {
      params: { year, month, ...params },
    })
  },
}
