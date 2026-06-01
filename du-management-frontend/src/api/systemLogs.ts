import http from './http'
import type {
  Page,
  SystemLogDetailResponse,
  SystemLogListResponse,
  SystemLogSearchParams,
  SystemLogSettingsResponse,
  SystemLogSettingsUpdateRequest,
} from '@/types'

export const systemLogsApi = {
  searchLogs(params: SystemLogSearchParams) {
    return http.get<Page<SystemLogListResponse>>('/system-logs', { params })
  },

  getLogDetail(id: number) {
    return http.get<SystemLogDetailResponse>(`/system-logs/${id}`)
  },

  getSettings() {
    return http.get<SystemLogSettingsResponse>('/system-logs/settings')
  },

  updateSettings(payload: SystemLogSettingsUpdateRequest) {
    return http.put<SystemLogSettingsResponse>('/system-logs/settings', payload)
  },
}
