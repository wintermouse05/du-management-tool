import http from './http'
import type {
  PointRuleRequest, PointRuleResponse,
  ManualPointRequest, PointHistoryResponse,
  LeaderboardEntryResponse, Page, Pageable
} from '@/types'

export const gamificationApi = {
  getRules(params?: Pageable) {
    return http.get<Page<PointRuleResponse>>('/gamification/rules', { params })
  },

  createRule(data: PointRuleRequest) {
    return http.post<PointRuleResponse>('/gamification/rules', data)
  },

  updateRule(id: number, data: PointRuleRequest) {
    return http.put<PointRuleResponse>(`/gamification/rules/${id}`, data)
  },

  adjustManual(data: ManualPointRequest) {
    return http.post<PointHistoryResponse>('/gamification/points/manual', data)
  },

  getUserHistory(userId: number, params?: Pageable) {
    return http.get<Page<PointHistoryResponse>>(`/gamification/points/history/${userId}`, { params })
  },

  getLeaderboard(params?: Pageable) {
    return http.get<Page<LeaderboardEntryResponse>>('/gamification/leaderboard', { params })
  },
}
