import http from './http'
import type { ChatopsLeaveRequestSummaryResponse } from '@/types'

export const chatopsApi = {
  getTodayLeaveRequests() {
    return http.get<ChatopsLeaveRequestSummaryResponse>('/chatops/leave-requests/today')
  },

  refreshTodayLeaveRequests() {
    return http.post<ChatopsLeaveRequestSummaryResponse>('/chatops/leave-requests/today/refresh')
  },
}
