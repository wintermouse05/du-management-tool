import http from './http'
import type {
  LuckyDrawSessionRequest, LuckyDrawSessionResponse,
  LuckyDrawPrizeRequest, LuckyDrawPrizeResponse,
  LuckyDrawParticipantResponse,
  LuckyDrawWinnerRequest, LuckyDrawWinnerResponse,
  Page, Pageable
} from '@/types'

export const luckyDrawApi = {
  createSession(data: LuckyDrawSessionRequest) {
    return http.post<LuckyDrawSessionResponse>('/lucky-draw/sessions', data)
  },

  getSessionsByEvent(eventId: number, params?: Pageable) {
    return http.get<Page<LuckyDrawSessionResponse>>('/lucky-draw/sessions', {
      params: { eventId, ...params },
    })
  },

  setupParticipants(sessionId: number, participantIds: number[]) {
    return http.post<LuckyDrawSessionResponse>(`/lucky-draw/sessions/${sessionId}/participants`, { participantIds })
  },

  getParticipants(sessionId: number) {
    return http.get<LuckyDrawParticipantResponse[]>(`/lucky-draw/sessions/${sessionId}/participants`)
  },

  createPrize(data: LuckyDrawPrizeRequest) {
    return http.post<LuckyDrawPrizeResponse>('/lucky-draw/prizes', data)
  },

  getPrizesBySession(sessionId: number, params?: Pageable) {
    return http.get<Page<LuckyDrawPrizeResponse>>('/lucky-draw/prizes', {
      params: { sessionId, ...params },
    })
  },

  drawWinner(data: LuckyDrawWinnerRequest) {
    return http.post<LuckyDrawWinnerResponse>('/lucky-draw/winners', data)
  },

  drawWinnerFromPool(prizeId: number) {
    return http.post<LuckyDrawWinnerResponse>(`/lucky-draw/prizes/${prizeId}/draw`)
  },

  getWinnersByPrize(prizeId: number, params?: Pageable) {
    return http.get<Page<LuckyDrawWinnerResponse>>('/lucky-draw/winners', {
      params: { prizeId, ...params },
    })
  },
}
